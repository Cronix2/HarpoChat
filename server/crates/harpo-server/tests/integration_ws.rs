// SPDX-License-Identifier: MIT
//! End-to-end integration tests for the HarpoChat relay.
//!
//! Spins up the real axum server on a random port and connects with a real
//! tokio-tungstenite client that performs Ed25519 authentication, sends an
//! envelope and verifies delivery to a second client.

use std::sync::Arc;
use std::sync::OnceLock;
use std::time::Duration;

use axum::serve;
use ed25519_dalek::{Signer, SigningKey};
use futures_util::{SinkExt, StreamExt};
use harpo_crypto::{challenge_digest, envelope_digest};
use harpo_proto::{ClientFrame, Envelope, ServerFrame};
use metrics_exporter_prometheus::{PrometheusBuilder, PrometheusHandle};
use tokio::net::TcpListener;
use tokio_tungstenite::tungstenite::protocol::Message as WsMessage;

fn metrics_handle() -> PrometheusHandle {
    static HANDLE: OnceLock<PrometheusHandle> = OnceLock::new();
    HANDLE
        .get_or_init(|| {
            PrometheusBuilder::new()
                .install_recorder()
                .expect("install prom recorder")
        })
        .clone()
}

async fn spawn_server() -> (String, tokio::task::JoinHandle<()>) {
    let state = harpo_server::AppState {
        mailbox: Arc::new(harpo_server::mailbox::MemoryMailbox::new()),
        sessions: Arc::new(harpo_server::session::SessionRegistry::new()),
        rate_limiter: Arc::new(harpo_server::rate_limit::RateLimiter::new(
            std::time::Duration::from_secs(60),
            10_000,
        )),
        metrics: metrics_handle(),
        server_version: harpo_server::SERVER_VERSION,
    };

    let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
    let addr = listener.local_addr().unwrap();
    let url = format!("ws://{}/v1/ws", addr);

    let app = harpo_server::build_router(state);
    let handle = tokio::spawn(async move {
        let _ = serve(
            listener,
            app.into_make_service_with_connect_info::<std::net::SocketAddr>(),
        )
        .await;
    });

    // Give it a moment to actually bind.
    tokio::time::sleep(Duration::from_millis(50)).await;
    (url, handle)
}

async fn connect_and_authenticate(
    url: &str,
    sk: &SigningKey,
) -> tokio_tungstenite::WebSocketStream<tokio_tungstenite::MaybeTlsStream<tokio::net::TcpStream>> {
    let (mut stream, _) = tokio_tungstenite::connect_async(url)
        .await
        .expect("connect");

    // 1. Hello
    let hello = ClientFrame::Hello {
        identity: sk.verifying_key().to_bytes(),
        version: harpo_server::SERVER_VERSION,
    };
    stream
        .send(WsMessage::Text(serde_json::to_string(&hello).unwrap()))
        .await
        .unwrap();

    // 2. Read Challenge
    let msg = stream.next().await.unwrap().unwrap();
    let text = msg.into_text().unwrap();
    let sf: ServerFrame = serde_json::from_str(&text).unwrap();
    let nonce = match sf {
        ServerFrame::Challenge { nonce, .. } => nonce,
        other => panic!("expected challenge, got {:?}", other),
    };

    // 3. AuthResponse
    let sig = sk.sign(&challenge_digest(&nonce)).to_bytes().to_vec();
    let resp = ClientFrame::AuthResponse { signature: sig };
    stream
        .send(WsMessage::Text(serde_json::to_string(&resp).unwrap()))
        .await
        .unwrap();

    // 4. Read Welcome
    let msg = stream.next().await.unwrap().unwrap();
    let text = msg.into_text().unwrap();
    let sf: ServerFrame = serde_json::from_str(&text).unwrap();
    assert!(
        matches!(sf, ServerFrame::Welcome { .. }),
        "expected welcome, got {:?}",
        sf
    );

    stream
}

#[tokio::test]
async fn full_handshake_and_relay() {
    let (url, _handle) = spawn_server().await;

    let alice_sk = SigningKey::generate(&mut rand::thread_rng());
    let bob_sk = SigningKey::generate(&mut rand::thread_rng());

    // Both connect and authenticate.
    let mut alice = connect_and_authenticate(&url, &alice_sk).await;
    let mut bob = connect_and_authenticate(&url, &bob_sk).await;

    // Alice sends an envelope to Bob.
    let ts_ms = 1_700_000_000_000i64;
    let ciphertext = b"this is opaque ciphertext".to_vec();
    let to = bob_sk.verifying_key().to_bytes();
    let from = alice_sk.verifying_key().to_bytes();
    let sig = alice_sk
        .sign(&envelope_digest(&to, ts_ms, &ciphertext))
        .to_bytes()
        .to_vec();

    let envelope = Envelope {
        from,
        to,
        ciphertext: ciphertext.clone(),
        signature: sig,
        ts_ms,
    };
    let send_frame = ClientFrame::Send { envelope };
    alice
        .send(WsMessage::Text(serde_json::to_string(&send_frame).unwrap()))
        .await
        .unwrap();

    // Alice should see a SendAck, Bob should receive a Deliver.
    // The order isn't deterministic; we poll both.
    let mut got_ack = false;
    let mut got_deliver = false;
    let deadline = tokio::time::Instant::now() + Duration::from_secs(2);

    while (!got_ack || !got_deliver) && tokio::time::Instant::now() < deadline {
        tokio::select! {
            msg = alice.next() => {
                if let Some(Ok(WsMessage::Text(t))) = msg {
                    let sf: ServerFrame = serde_json::from_str(&t).unwrap();
                    if let ServerFrame::SendAck { stored, .. } = sf {
                        // Bob is online so it should be relayed (stored == false)
                        assert!(!stored, "should have been relayed, not stored");
                        got_ack = true;
                    }
                }
            }
            msg = bob.next() => {
                if let Some(Ok(WsMessage::Text(t))) = msg {
                    let sf: ServerFrame = serde_json::from_str(&t).unwrap();
                    if let ServerFrame::Deliver { envelope, .. } = sf {
                        assert_eq!(envelope.ciphertext, ciphertext);
                        assert_eq!(envelope.from, from);
                        got_deliver = true;
                    }
                }
            }
            _ = tokio::time::sleep(Duration::from_millis(50)) => {}
        }
    }

    assert!(got_ack, "alice never got SendAck");
    assert!(got_deliver, "bob never got Deliver");
}

#[tokio::test]
async fn offline_recipient_gets_queued_message() {
    let (url, _handle) = spawn_server().await;

    let alice_sk = SigningKey::generate(&mut rand::thread_rng());
    let bob_sk = SigningKey::generate(&mut rand::thread_rng());
    let bob_pk = bob_sk.verifying_key().to_bytes();

    // Only Alice is online.
    let mut alice = connect_and_authenticate(&url, &alice_sk).await;

    let ts_ms = 1_700_000_000_001;
    let ciphertext = b"queued for bob".to_vec();
    let sig = alice_sk
        .sign(&envelope_digest(&bob_pk, ts_ms, &ciphertext))
        .to_bytes()
        .to_vec();
    let send_frame = ClientFrame::Send {
        envelope: Envelope {
            from: alice_sk.verifying_key().to_bytes(),
            to: bob_pk,
            ciphertext: ciphertext.clone(),
            signature: sig,
            ts_ms,
        },
    };
    alice
        .send(WsMessage::Text(serde_json::to_string(&send_frame).unwrap()))
        .await
        .unwrap();

    // Alice sees a SendAck with stored=true
    let mut saw_stored_ack = false;
    let deadline = tokio::time::Instant::now() + Duration::from_secs(2);
    while tokio::time::Instant::now() < deadline {
        let msg = tokio::time::timeout(Duration::from_millis(200), alice.next()).await;
        if let Ok(Some(Ok(WsMessage::Text(t)))) = msg {
            if let Ok(ServerFrame::SendAck { stored, .. }) = serde_json::from_str::<ServerFrame>(&t)
            {
                assert!(stored, "expected stored=true for offline peer");
                saw_stored_ack = true;
                break;
            }
        }
    }
    assert!(saw_stored_ack);

    // Bob connects later and should immediately receive the queued envelope.
    let mut bob = connect_and_authenticate(&url, &bob_sk).await;
    let mut got_it = false;
    let deadline = tokio::time::Instant::now() + Duration::from_secs(2);
    while tokio::time::Instant::now() < deadline {
        let msg = tokio::time::timeout(Duration::from_millis(200), bob.next()).await;
        if let Ok(Some(Ok(WsMessage::Text(t)))) = msg {
            if let Ok(ServerFrame::Deliver { envelope, .. }) =
                serde_json::from_str::<ServerFrame>(&t)
            {
                assert_eq!(envelope.ciphertext, ciphertext);
                got_it = true;
                break;
            }
        }
    }
    assert!(got_it, "bob never received queued envelope");
}

#[tokio::test]
async fn auth_with_bad_signature_is_rejected() {
    let (url, _handle) = spawn_server().await;
    let sk = SigningKey::generate(&mut rand::thread_rng());

    let (mut stream, _) = tokio_tungstenite::connect_async(&url).await.unwrap();
    let hello = ClientFrame::Hello {
        identity: sk.verifying_key().to_bytes(),
        version: harpo_server::SERVER_VERSION,
    };
    stream
        .send(WsMessage::Text(serde_json::to_string(&hello).unwrap()))
        .await
        .unwrap();

    let msg = stream.next().await.unwrap().unwrap().into_text().unwrap();
    let _sf: ServerFrame = serde_json::from_str(&msg).unwrap();

    // Send random garbage as signature
    let resp = ClientFrame::AuthResponse {
        signature: vec![0xDEu8; 64],
    };
    stream
        .send(WsMessage::Text(serde_json::to_string(&resp).unwrap()))
        .await
        .unwrap();

    let msg = stream.next().await.unwrap().unwrap().into_text().unwrap();
    let sf: ServerFrame = serde_json::from_str(&msg).unwrap();
    match sf {
        ServerFrame::Error { code, .. } => {
            assert_eq!(code, harpo_proto::ErrorCode::AuthFailed);
        }
        other => panic!("expected auth_failed, got {:?}", other),
    }
}
