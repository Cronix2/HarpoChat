// SPDX-License-Identifier: MIT
//! HarpoChat wire protocol.
//!
//! All frames are JSON objects on the `/v1/ws` WebSocket. The server is a
//! minimal relay: it never sees plaintext message contents. Message bodies are
//! opaque byte blobs (base64-encoded in JSON) encrypted client-side with the
//! Signal protocol.

use serde::{Deserialize, Serialize};

pub mod b64 {
    use base64::Engine;
    use serde::{Deserialize, Deserializer, Serializer};

    pub fn serialize<S: Serializer>(bytes: &[u8], s: S) -> Result<S::Ok, S::Error> {
        s.serialize_str(&base64::engine::general_purpose::STANDARD.encode(bytes))
    }

    pub fn deserialize<'de, D: Deserializer<'de>>(d: D) -> Result<Vec<u8>, D::Error> {
        let s = String::deserialize(d)?;
        base64::engine::general_purpose::STANDARD
            .decode(s.as_bytes())
            .map_err(serde::de::Error::custom)
    }
}

/// 32-byte Ed25519 public key used as the stable identity of a device.
pub type IdentityPubKey = [u8; 32];

/// Client → Server frames.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum ClientFrame {
    /// First frame after WS upgrade: client announces which identity it is.
    Hello {
        #[serde(with = "b64_array")]
        identity: IdentityPubKey,
        /// Client-advertised protocol version.
        version: u16,
    },

    /// Response to a server Challenge: Ed25519 signature over the challenge nonce.
    AuthResponse {
        #[serde(with = "b64")]
        signature: Vec<u8>,
    },

    /// Publish the client's current PreKey bundle so peers can start a Signal
    /// session without a round-trip to the owner.
    PublishPreKeys {
        #[serde(with = "b64")]
        bundle: Vec<u8>,
    },

    /// Request the published PreKey bundle of a peer.
    FetchPreKeys {
        #[serde(with = "b64_array")]
        peer: IdentityPubKey,
    },

    /// Send an encrypted envelope to a peer. Server stores it until the peer
    /// comes online, then relays it.
    Send {
        envelope: Envelope,
    },

    /// Acknowledge receipt of an envelope (lets the server drop it from the mailbox).
    Ack {
        message_id: uuid::Uuid,
    },

    /// Application-level heartbeat (WebSocket pings are also supported).
    Ping {
        ts_ms: i64,
    },
}

/// Server → Client frames.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum ServerFrame {
    /// Server greets the client and issues a challenge to prove possession of
    /// the private identity key.
    Challenge {
        #[serde(with = "b64")]
        nonce: Vec<u8>,
        server_version: u16,
    },

    /// Authentication succeeded.
    Welcome {
        session_id: uuid::Uuid,
    },

    /// Authentication failed / frame rejected.
    Error {
        code: ErrorCode,
        message: String,
    },

    /// Server delivers an envelope previously queued for this identity.
    Deliver {
        message_id: uuid::Uuid,
        envelope: Envelope,
    },

    /// Response to FetchPreKeys.
    PreKeys {
        #[serde(with = "b64_array")]
        peer: IdentityPubKey,
        #[serde(with = "b64")]
        bundle: Vec<u8>,
    },

    /// Response to Send: the envelope was accepted and stored (or forwarded).
    SendAck {
        message_id: uuid::Uuid,
        stored: bool,
    },

    /// Response to application Ping.
    Pong {
        ts_ms: i64,
    },
}

/// An opaque encrypted message destined for a peer. The server never decrypts
/// `ciphertext`; it only routes based on `to`.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Envelope {
    #[serde(with = "b64_array")]
    pub from: IdentityPubKey,
    #[serde(with = "b64_array")]
    pub to: IdentityPubKey,
    /// Signal-protocol ciphertext (PreKeySignalMessage or SignalMessage bytes).
    #[serde(with = "b64")]
    pub ciphertext: Vec<u8>,
    /// Detached Ed25519 signature over `ciphertext || to || ts_ms` by `from`.
    /// Lets the server (and the recipient) verify the envelope author.
    #[serde(with = "b64")]
    pub signature: Vec<u8>,
    /// Sender's timestamp (milliseconds since Unix epoch). Advisory only.
    pub ts_ms: i64,
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub enum ErrorCode {
    BadFrame,
    NotAuthenticated,
    AuthFailed,
    RateLimited,
    PeerUnknown,
    PayloadTooLarge,
    Internal,
}

/// Base64 codec for fixed-size byte arrays.
mod b64_array {
    use base64::Engine;
    use serde::{Deserialize, Deserializer, Serializer};

    pub fn serialize<S: Serializer, const N: usize>(
        bytes: &[u8; N],
        s: S,
    ) -> Result<S::Ok, S::Error> {
        s.serialize_str(&base64::engine::general_purpose::STANDARD.encode(bytes))
    }

    pub fn deserialize<'de, D: Deserializer<'de>, const N: usize>(
        d: D,
    ) -> Result<[u8; N], D::Error> {
        let s = String::deserialize(d)?;
        let v = base64::engine::general_purpose::STANDARD
            .decode(s.as_bytes())
            .map_err(serde::de::Error::custom)?;
        v.try_into().map_err(|_| {
            serde::de::Error::custom(format!("expected {N} bytes, got wrong length"))
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn envelope_roundtrip() {
        let env = Envelope {
            from: [1u8; 32],
            to: [2u8; 32],
            ciphertext: vec![0xAA, 0xBB, 0xCC],
            signature: vec![0u8; 64],
            ts_ms: 1_700_000_000_000,
        };
        let s = serde_json::to_string(&env).unwrap();
        let parsed: Envelope = serde_json::from_str(&s).unwrap();
        assert_eq!(parsed.from, env.from);
        assert_eq!(parsed.to, env.to);
        assert_eq!(parsed.ciphertext, env.ciphertext);
    }

    #[test]
    fn client_frame_send_roundtrip() {
        let f = ClientFrame::Send {
            envelope: Envelope {
                from: [7u8; 32],
                to: [9u8; 32],
                ciphertext: b"hello".to_vec(),
                signature: vec![0; 64],
                ts_ms: 1,
            },
        };
        let s = serde_json::to_string(&f).unwrap();
        assert!(s.contains("\"type\":\"send\""));
        let _back: ClientFrame = serde_json::from_str(&s).unwrap();
    }

    #[test]
    fn server_frame_error_shape() {
        let f = ServerFrame::Error {
            code: ErrorCode::AuthFailed,
            message: "bad signature".to_string(),
        };
        let s = serde_json::to_string(&f).unwrap();
        assert!(s.contains("\"type\":\"error\""));
        assert!(s.contains("\"code\":\"auth_failed\""));
    }
}
