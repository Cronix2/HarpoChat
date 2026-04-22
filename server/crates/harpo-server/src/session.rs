// SPDX-License-Identifier: MIT
//! Registry of connected, authenticated identities and their outbound channels.

use dashmap::DashMap;
use harpo_proto::{IdentityPubKey, ServerFrame};
use tokio::sync::mpsc;
use uuid::Uuid;

pub struct Session {
    pub session_id: Uuid,
    pub identity: IdentityPubKey,
    pub tx: mpsc::Sender<ServerFrame>,
}

pub struct SessionRegistry {
    inner: DashMap<IdentityPubKey, Session>,
}

impl SessionRegistry {
    pub fn new() -> Self {
        Self { inner: DashMap::new() }
    }

    /// Register a session. Returns the previous session for this identity, if any
    /// (caller should close it — at most one active socket per identity).
    pub fn insert(&self, session: Session) -> Option<Session> {
        self.inner.insert(session.identity, session)
    }

    pub fn remove(&self, identity: &IdentityPubKey) {
        self.inner.remove(identity);
    }

    pub fn tx_for(&self, identity: &IdentityPubKey) -> Option<mpsc::Sender<ServerFrame>> {
        self.inner.get(identity).map(|s| s.tx.clone())
    }

    pub fn len(&self) -> usize {
        self.inner.len()
    }

    pub fn is_empty(&self) -> bool {
        self.inner.is_empty()
    }
}

impl Default for SessionRegistry {
    fn default() -> Self {
        Self::new()
    }
}
