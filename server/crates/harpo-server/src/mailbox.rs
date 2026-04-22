// SPDX-License-Identifier: MIT
//! Encrypted envelope mailbox.
//!
//! Stores ciphertext addressed to an identity until the identity comes online
//! and acknowledges delivery. Two implementations:
//!   * `MemoryMailbox` — in-process, used for tests and dev.
//!   * `SqliteMailbox` — durable, used in production.

use std::collections::HashMap;
use std::sync::Mutex;

use async_trait::async_trait;
use chrono::Utc;
use harpo_proto::{Envelope, IdentityPubKey};
use sqlx::sqlite::SqlitePoolOptions;
use sqlx::SqlitePool;
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

// ============================================================================
// In-memory implementation.
// ============================================================================

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
        // Non-destructive read: messages are only removed by `ack` (or by the
        // recipient sending the corresponding Ack frame). This guarantees
        // at-least-once delivery: a client that disconnects before ack'ing
        // will receive the envelope again on reconnect.
        let guard = self.inner.lock().expect("mailbox poisoned");
        Ok(guard.get(identity).cloned().unwrap_or_default())
    }

    async fn ack(&self, identity: &IdentityPubKey, id: Uuid) -> anyhow::Result<bool> {
        let mut guard = self.inner.lock().expect("mailbox poisoned");
        if let Some(queue) = guard.get_mut(identity) {
            let before = queue.len();
            queue.retain(|m| m.id != id);
            if queue.is_empty() {
                guard.remove(identity);
            }
            Ok(guard.get(identity).map(|q| q.len()).unwrap_or(0) != before)
        } else {
            Ok(false)
        }
    }
}

// ============================================================================
// SQLite-backed implementation.
// ============================================================================

pub struct SqliteMailbox {
    pool: SqlitePool,
}

impl SqliteMailbox {
    /// Connect to a SQLite database and run migrations.
    /// `url` is a standard sqlx SQLite URL, e.g. `sqlite://harpo.db?mode=rwc`.
    pub async fn connect(url: &str) -> anyhow::Result<Self> {
        let pool = SqlitePoolOptions::new()
            .max_connections(16)
            .connect(url)
            .await?;
        // Inline migration. Keeps us free of sqlx-cli tooling and avoids a
        // build-time DATABASE_URL dependency.
        sqlx::query(include_str!(
            "../migrations/0001_pending_envelopes.sql"
        ))
        .execute(&pool)
        .await?;
        Ok(Self { pool })
    }

    pub fn pool(&self) -> &SqlitePool {
        &self.pool
    }
}

#[async_trait]
impl Mailbox for SqliteMailbox {
    async fn push(&self, msg: StoredMessage) -> anyhow::Result<()> {
        let now = Utc::now().timestamp_millis();
        let id_str = msg.id.to_string();
        let to_v = msg.envelope.to.to_vec();
        let from_v = msg.envelope.from.to_vec();
        sqlx::query(
            r#"INSERT INTO pending_envelopes
               (id, to_identity, from_identity, ciphertext, signature, ts_ms, received_at)
               VALUES (?, ?, ?, ?, ?, ?, ?)"#,
        )
        .bind(id_str)
        .bind(to_v)
        .bind(from_v)
        .bind(msg.envelope.ciphertext.clone())
        .bind(msg.envelope.signature.clone())
        .bind(msg.envelope.ts_ms)
        .bind(now)
        .execute(&self.pool)
        .await?;
        Ok(())
    }

    async fn drain_for(&self, identity: &IdentityPubKey) -> anyhow::Result<Vec<StoredMessage>> {
        let rows: Vec<(String, Vec<u8>, Vec<u8>, Vec<u8>, i64)> = sqlx::query_as(
            r#"SELECT id, from_identity, ciphertext, signature, ts_ms
               FROM pending_envelopes
               WHERE to_identity = ?
               ORDER BY received_at ASC"#,
        )
        .bind(identity.to_vec())
        .fetch_all(&self.pool)
        .await?;

        let mut out = Vec::with_capacity(rows.len());
        for (id_s, from_v, ct, sig, ts_ms) in rows {
            let id = Uuid::parse_str(&id_s)?;
            let from: IdentityPubKey = from_v
                .try_into()
                .map_err(|_| anyhow::anyhow!("from column has wrong length"))?;
            out.push(StoredMessage {
                id,
                envelope: Envelope {
                    from,
                    to: *identity,
                    ciphertext: ct,
                    signature: sig,
                    ts_ms,
                },
            });
        }
        Ok(out)
    }

    async fn ack(&self, identity: &IdentityPubKey, id: Uuid) -> anyhow::Result<bool> {
        let res = sqlx::query(
            r#"DELETE FROM pending_envelopes WHERE id = ? AND to_identity = ?"#,
        )
        .bind(id.to_string())
        .bind(identity.to_vec())
        .execute(&self.pool)
        .await?;
        Ok(res.rows_affected() > 0)
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
    async fn memory_push_and_drain() {
        let mb = MemoryMailbox::new();
        mb.push(StoredMessage { id: Uuid::new_v4(), envelope: env([2u8; 32]) })
            .await
            .unwrap();
        let got = mb.drain_for(&[2u8; 32]).await.unwrap();
        assert_eq!(got.len(), 1);
        // Non-destructive: still there on re-drain.
        let again = mb.drain_for(&[2u8; 32]).await.unwrap();
        assert_eq!(again.len(), 1);
        // Ack removes.
        assert!(mb.ack(&[2u8; 32], got[0].id).await.unwrap());
        let empty = mb.drain_for(&[2u8; 32]).await.unwrap();
        assert!(empty.is_empty());
    }

    #[tokio::test]
    async fn sqlite_push_drain_ack() {
        let mb = SqliteMailbox::connect("sqlite::memory:").await.unwrap();
        let id = Uuid::new_v4();
        mb.push(StoredMessage { id, envelope: env([3u8; 32]) })
            .await
            .unwrap();

        // push a second message for the same recipient
        let id2 = Uuid::new_v4();
        mb.push(StoredMessage { id: id2, envelope: env([3u8; 32]) })
            .await
            .unwrap();

        let drained = mb.drain_for(&[3u8; 32]).await.unwrap();
        assert_eq!(drained.len(), 2);

        // drain does not delete (ack does); so re-drain returns the same rows
        let redrained = mb.drain_for(&[3u8; 32]).await.unwrap();
        assert_eq!(redrained.len(), 2);

        assert!(mb.ack(&[3u8; 32], id).await.unwrap());
        let after = mb.drain_for(&[3u8; 32]).await.unwrap();
        assert_eq!(after.len(), 1);
        assert_eq!(after[0].id, id2);
    }
}
