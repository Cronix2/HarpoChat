// SPDX-License-Identifier: MIT
//! WebSocket handler implementing the HarpoChat relay protocol.

use std::net::SocketAddr;

use axum::{
    extract::{
        connect_info::ConnectInfo,
        ws::{Message, WebSocket, WebSocketUpgrade},
        State,
    },
    response::IntoResponse,
};
use futures_util::StreamExt;
use harpo_crypto::{new_nonce, verify_challenge, verify_envelope};
use harpo_proto::{ClientFrame, Envelope, ErrorCode, IdentityPubKey, ServerFrame};
use tokio::sync::mpsc;
use tracing::{debug, info, warn};
use uuid::Uuid;

use crate::mailbox::StoredMessage;
use crate::session::Session;
use crate::AppState;

const SEND_BUFFER: usize = 64;
const MAX_CIPHERTEXT_BYTES: usize = 64 * 1024;

pub async fn ws_handler(
    ws: WebSocketUpgrade,
    ConnectInfo(peer): ConnectInfo<SocketAddr>,
    State(state): State<AppState>,
) -> impl IntoResponse {
    ws.on_upgrade(move |socket| handle_socket(socket, state, peer))
}

async fn handle_socket(mut socket: WebSocket, state: AppState, peer: SocketAddr) {
    metrics::counter!("harpo_ws_connections_total").increment(1);
    debug!(%peer, "ws upgraded");

    // --- AUTH HANDSHAKE ------------------------------------------------------
    let Some(identity) = run_auth(&mut socket, &state).await else {
        metrics::counter!("harpo_ws_auth_failed_total").increment(1);
        return;
    };

    // --- REGISTER SESSION ----------------------------------------------------
    let (tx_out, mut rx_out) = mpsc::channel::<ServerFrame>(SEND_BUFFER);
    let session_id = Uuid::new_v4();
    if let Some(previous) = state.sessions.insert(Session {
        session_id,
        identity,
        tx: tx_out.clone(),
    }) {
        let _ = previous
            .tx
            .send(ServerFrame::Error {
                code: ErrorCode::NotAuthenticated,
                message: "replaced by newer session".to_string(),
            })
            .await;
    }
    let _ = send(&mut socket, &ServerFrame::Welcome { session_id }).await;

    // Flush any queued envelopes.
    flush_pending(&mut socket, &state, &identity).await;

    // --- MAIN LOOP -----------------------------------------------------------
    loop {
        tokio::select! {
            outgoing = rx_out.recv() => {
                match outgoing {
                    Some(frame) => {
                        if send(&mut socket, &frame).await.is_err() {
                            break;
                        }
                    }
                    None => break,
                }
            }
            incoming = socket.next() => {
                let Some(Ok(msg)) = incoming else { break };
                match msg {
                    Message::Text(t) => {
                        if !handle_client_frame(&mut socket, &state, &identity, &t).await {
                            break;
                        }
                    }
                    Message::Binary(_) => {
                        let _ = send(&mut socket, &ServerFrame::Error {
                            code: ErrorCode::BadFrame,
                            message: "binary frames not supported; use JSON text".to_string(),
                        }).await;
                    }
                    Message::Close(_) => break,
                    Message::Ping(_) | Message::Pong(_) => {}
                }
            }
        }
    }

    state.sessions.remove(&identity);
    debug!(%peer, "ws closed");
    metrics::counter!("harpo_ws_disconnects_total").increment(1);
}

async fn run_auth(socket: &mut WebSocket, state: &AppState) -> Option<IdentityPubKey> {
    // Expect Hello first.
    let hello_txt = read_text(socket).await?;
    let Ok(ClientFrame::Hello { identity, version }) = serde_json::from_str(&hello_txt) else {
        let _ = send(
            socket,
            &ServerFrame::Error {
                code: ErrorCode::BadFrame,
                message: "expected hello frame first".to_string(),
            },
        )
        .await;
        return None;
    };
    if version != state.server_version {
        let _ = send(
            socket,
            &ServerFrame::Error {
                code: ErrorCode::BadFrame,
                message: format!("unsupported client version {version}"),
            },
        )
        .await;
        return None;
    }

    // Send Challenge.
    let nonce = new_nonce();
    if send(
        socket,
        &ServerFrame::Challenge {
            nonce: nonce.to_vec(),
            server_version: state.server_version,
        },
    )
    .await
    .is_err()
    {
        return None;
    }

    // Expect AuthResponse.
    let resp_txt = read_text(socket).await?;
    let Ok(ClientFrame::AuthResponse { signature }) = serde_json::from_str(&resp_txt) else {
        let _ = send(
            socket,
            &ServerFrame::Error {
                code: ErrorCode::BadFrame,
                message: "expected auth_response".to_string(),
            },
        )
        .await;
        return None;
    };

    if let Err(e) = verify_challenge(&identity, &nonce, &signature) {
        warn!(?e, "auth failed");
        let _ = send(
            socket,
            &ServerFrame::Error {
                code: ErrorCode::AuthFailed,
                message: "signature does not verify".to_string(),
            },
        )
        .await;
        return None;
    }

    metrics::counter!("harpo_ws_auth_ok_total").increment(1);
    info!(identity = %hex::encode(&identity), "session authenticated");
    Some(identity)
}

async fn read_text(socket: &mut WebSocket) -> Option<String> {
    loop {
        match socket.next().await? {
            Ok(Message::Text(t)) => return Some(t),
            Ok(Message::Ping(_)) | Ok(Message::Pong(_)) => continue,
            Ok(Message::Close(_)) | Err(_) => return None,
            Ok(Message::Binary(_)) => return None,
        }
    }
}

async fn send(socket: &mut WebSocket, frame: &ServerFrame) -> Result<(), axum::Error> {
    let t = serde_json::to_string(frame).expect("server frames serialize");
    socket.send(Message::Text(t)).await
}

async fn flush_pending(socket: &mut WebSocket, state: &AppState, identity: &IdentityPubKey) {
    match state.mailbox.drain_for(identity).await {
        Ok(pending) => {
            for StoredMessage { id, envelope } in pending {
                let _ = send(
                    socket,
                    &ServerFrame::Deliver {
                        message_id: id,
                        envelope,
                    },
                )
                .await;
            }
        }
        Err(e) => warn!(?e, "drain failed"),
    }
}

async fn handle_client_frame(
    socket: &mut WebSocket,
    state: &AppState,
    identity: &IdentityPubKey,
    text: &str,
) -> bool {
    let frame: ClientFrame = match serde_json::from_str(text) {
        Ok(f) => f,
        Err(e) => {
            let _ = send(
                socket,
                &ServerFrame::Error {
                    code: ErrorCode::BadFrame,
                    message: format!("invalid json: {e}"),
                },
            )
            .await;
            return true;
        }
    };

    match frame {
        ClientFrame::Hello { .. } | ClientFrame::AuthResponse { .. } => {
            let _ = send(
                socket,
                &ServerFrame::Error {
                    code: ErrorCode::BadFrame,
                    message: "already authenticated".to_string(),
                },
            )
            .await;
        }
        ClientFrame::Ping { ts_ms } => {
            let _ = send(socket, &ServerFrame::Pong { ts_ms }).await;
        }
        ClientFrame::Send { envelope } => {
            handle_send(socket, state, identity, envelope).await;
        }
        ClientFrame::Ack { message_id } => {
            let _ = state.mailbox.ack(identity, message_id).await;
        }
        ClientFrame::PublishPreKeys { .. } | ClientFrame::FetchPreKeys { .. } => {
            // MVP: PreKey publication is not yet implemented on the server side.
            let _ = send(
                socket,
                &ServerFrame::Error {
                    code: ErrorCode::Internal,
                    message: "prekey routes not yet implemented".to_string(),
                },
            )
            .await;
        }
    }
    true
}

async fn handle_send(
    socket: &mut WebSocket,
    state: &AppState,
    sender: &IdentityPubKey,
    envelope: Envelope,
) {
    if envelope.from != *sender {
        let _ = send(
            socket,
            &ServerFrame::Error {
                code: ErrorCode::AuthFailed,
                message: "envelope `from` does not match authenticated identity".to_string(),
            },
        )
        .await;
        return;
    }
    if envelope.ciphertext.len() > MAX_CIPHERTEXT_BYTES {
        let _ = send(
            socket,
            &ServerFrame::Error {
                code: ErrorCode::PayloadTooLarge,
                message: format!("ciphertext exceeds {MAX_CIPHERTEXT_BYTES} bytes"),
            },
        )
        .await;
        return;
    }
    if !state.rate_limiter.check(sender) {
        metrics::counter!("harpo_rate_limit_hits_total").increment(1);
        let _ = send(
            socket,
            &ServerFrame::Error {
                code: ErrorCode::RateLimited,
                message: "per-identity send rate limit exceeded".to_string(),
            },
        )
        .await;
        return;
    }
    if let Err(e) = verify_envelope(
        &envelope.from,
        &envelope.to,
        envelope.ts_ms,
        &envelope.ciphertext,
        &envelope.signature,
    ) {
        let _ = send(
            socket,
            &ServerFrame::Error {
                code: ErrorCode::AuthFailed,
                message: format!("envelope signature invalid: {e}"),
            },
        )
        .await;
        return;
    }

    let msg = StoredMessage {
        id: Uuid::new_v4(),
        envelope: envelope.clone(),
    };

    // If recipient is online, relay immediately (still also drop in mailbox for
    // at-least-once semantics; the recipient Acks to drop).
    let stored;
    if let Some(tx) = state.sessions.tx_for(&envelope.to) {
        let _ = tx
            .send(ServerFrame::Deliver {
                message_id: msg.id,
                envelope: msg.envelope.clone(),
            })
            .await;
        stored = false;
        metrics::counter!("harpo_envelopes_relayed_total").increment(1);
    } else {
        let _ = state.mailbox.push(msg.clone()).await;
        stored = true;
        metrics::counter!("harpo_envelopes_stored_total").increment(1);
    }

    let _ = send(
        socket,
        &ServerFrame::SendAck {
            message_id: msg.id,
            stored,
        },
    )
    .await;
}

// hex only used for logs; avoid pulling full hex crate by implementing a small encode.
mod hex {
    pub fn encode(bytes: &[u8]) -> String {
        let mut s = String::with_capacity(bytes.len() * 2);
        for b in bytes {
            s.push_str(&format!("{:02x}", b));
        }
        s
    }
}
