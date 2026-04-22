// SPDX-License-Identifier: MIT
//! Encrypted envelope mailbox.
//!
//! Stores ciphertext addressed to an identity until the identity comes online
//! and acknowledges delivery. The in-memory impl is used for tests and dev;
//! production uses SQLite/PostgreSQL (see `SqliteMailbox`).

use std::collections::HashMap;
use std::sync::Mutex;

use async_trait::async_trait;
use harpo_proto::{Envelope, IdentityPubKey};
use uuid::Uuid;

#[derive(Debug, Clone)]
pub struct StoredMessage {
    pub id: Uuid,
    pub envelope: Envelope,
}

#[async_trait]
pub trait Mailbox: Send + Sync {
    async fn push(&self, msg: StoredMessage) -> anyhow::Result<()>;
    async fn drain_for(&self, identity: &IdentityPubKey) -> anyhow::Result<Vec<StoredMessage>>;
    async fn ack(&self, identity: &IdentityPubKey, id: Uuid) -> anyhow::Result<bool>;
}

pub struct MemoryMailbox {
    inner: Mutex<HashMap<IdentityPubKey, Vec<StoredMessage>>>,
}

impl MemoryMailbox {
    pub fn new() -> Self {
        Self { inner: Mutex::new(HashMap::new()) }
    }
}

impl Default for MemoryMailbox {
    fn default() -> Self {
        Self::new()
    }
}

#[async_trait]
impl Mailbox for MemoryMailbox {
    async fn push(&self, msg: StoredMessage) -> anyhow::Result<()> {
        let to = msg.envelope.to;
        let mut guard = self.inner.lock().expect("mailbox poisoned");
        guard.entry(to).or_default().push(msg);
        Ok(())
    }

    async fn drain_for(&self, identity: &IdentityPubKey) -> anyhow::Result<Vec<StoredMessage>> {
        let mut guard = self.inner.lock().expect("mailbox poisoned");
        Ok(guard.remove(identity).unwrap_or_default())
    }

    async fn ack(&self, identity: &IdentityPubKey, id: Uuid) -> anyhow::Result<bool> {
        let mut guard = self.inner.lock().expect("mailbox poisoned");
        if let Some(queue) = guard.get_mut(identity) {
            let before = queue.len();
            queue.retain(|m| m.id != id);
            Ok(queue.len() != before)
        } else {
            Ok(false)
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn env(to: [u8; 32]) -> Envelope {
        Envelope {
            from: [1u8; 32],
            to,
            ciphertext: b"x".to_vec(),
            signature: vec![0u8; 64],
            ts_ms: 1,
        }
    }

    #[tokio::test]
    async fn push_and_drain() {
        let mb = MemoryMailbox::new();
        mb.push(StoredMessage { id: Uuid::new_v4(), envelope: env([2u8; 32]) })
            .await
            .unwrap();
        let got = mb.drain_for(&[2u8; 32]).await.unwrap();
        assert_eq!(got.len(), 1);
        let empty = mb.drain_for(&[2u8; 32]).await.unwrap();
        assert!(empty.is_empty());
    }
}
