// SPDX-License-Identifier: MIT
//! Ed25519 challenge / response authentication primitives.
//!
//! The server proves control-of-identity by asking the client to sign a fresh
//! server-generated nonce with its Ed25519 identity key. The public key is the
//! user's stable device identity.

use ed25519_dalek::{Signature, Verifier, VerifyingKey};
use rand::RngCore;
use sha2::{Digest, Sha256};

pub const NONCE_LEN: usize = 32;
pub const DOMAIN: &[u8] = b"harpochat/v1/auth-challenge";

#[derive(Debug, thiserror::Error)]
pub enum AuthError {
    #[error("invalid signature encoding")]
    BadSignature,
    #[error("invalid public key")]
    BadPublicKey,
    #[error("signature verification failed")]
    VerifyFailed,
}

/// Generate a fresh 32-byte challenge nonce.
pub fn new_nonce() -> [u8; NONCE_LEN] {
    let mut buf = [0u8; NONCE_LEN];
    rand::thread_rng().fill_bytes(&mut buf);
    buf
}

/// Compute the canonical message to be signed: SHA256(DOMAIN || nonce).
/// Domain-separated so the same identity key can never be tricked into signing
/// a challenge that collides with a Signal or envelope signature.
pub fn challenge_digest(nonce: &[u8]) -> [u8; 32] {
    let mut h = Sha256::new();
    h.update(DOMAIN);
    h.update(nonce);
    let out = h.finalize();
    let mut arr = [0u8; 32];
    arr.copy_from_slice(&out);
    arr
}

/// Verify that `signature` is a valid Ed25519 signature, by `public_key`, over
/// `challenge_digest(nonce)`.
pub fn verify_challenge(
    public_key: &[u8; 32],
    nonce: &[u8],
    signature: &[u8],
) -> Result<(), AuthError> {
    let vk = VerifyingKey::from_bytes(public_key).map_err(|_| AuthError::BadPublicKey)?;
    let sig_bytes: [u8; 64] = signature.try_into().map_err(|_| AuthError::BadSignature)?;
    let sig = Signature::from_bytes(&sig_bytes);
    let msg = challenge_digest(nonce);
    vk.verify(&msg, &sig).map_err(|_| AuthError::VerifyFailed)
}

/// Verify that `signature` is a valid Ed25519 signature over an envelope.
/// The signed message is SHA256("harpochat/v1/envelope" || to || ts_ms_le || ciphertext).
pub fn verify_envelope(
    from: &[u8; 32],
    to: &[u8; 32],
    ts_ms: i64,
    ciphertext: &[u8],
    signature: &[u8],
) -> Result<(), AuthError> {
    let vk = VerifyingKey::from_bytes(from).map_err(|_| AuthError::BadPublicKey)?;
    let sig_bytes: [u8; 64] = signature.try_into().map_err(|_| AuthError::BadSignature)?;
    let sig = Signature::from_bytes(&sig_bytes);
    let msg = envelope_digest(to, ts_ms, ciphertext);
    vk.verify(&msg, &sig).map_err(|_| AuthError::VerifyFailed)
}

pub fn envelope_digest(to: &[u8; 32], ts_ms: i64, ciphertext: &[u8]) -> [u8; 32] {
    let mut h = Sha256::new();
    h.update(b"harpochat/v1/envelope");
    h.update(to);
    h.update(ts_ms.to_le_bytes());
    h.update(ciphertext);
    let out = h.finalize();
    let mut arr = [0u8; 32];
    arr.copy_from_slice(&out);
    arr
}

#[cfg(test)]
mod tests {
    use super::*;
    use ed25519_dalek::{Signer, SigningKey};

    #[test]
    fn challenge_roundtrip() {
        let sk = SigningKey::generate(&mut rand::thread_rng());
        let pk: [u8; 32] = sk.verifying_key().to_bytes();
        let nonce = new_nonce();
        let msg = challenge_digest(&nonce);
        let sig = sk.sign(&msg).to_bytes();
        verify_challenge(&pk, &nonce, &sig).unwrap();
    }

    #[test]
    fn challenge_rejects_wrong_nonce() {
        let sk = SigningKey::generate(&mut rand::thread_rng());
        let pk: [u8; 32] = sk.verifying_key().to_bytes();
        let nonce = new_nonce();
        let msg = challenge_digest(&nonce);
        let sig = sk.sign(&msg).to_bytes();

        let mut bad = nonce;
        bad[0] ^= 0xFF;
        assert!(verify_challenge(&pk, &bad, &sig).is_err());
    }

    #[test]
    fn envelope_signature_roundtrip() {
        let sk = SigningKey::generate(&mut rand::thread_rng());
        let from: [u8; 32] = sk.verifying_key().to_bytes();
        let to = [7u8; 32];
        let ct = b"encrypted-blob";
        let ts = 1_700_000_000_000i64;
        let sig = sk.sign(&envelope_digest(&to, ts, ct)).to_bytes();
        verify_envelope(&from, &to, ts, ct, &sig).unwrap();
    }
}
