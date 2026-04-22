// SPDX-License-Identifier: MIT
//! Per-identity sliding-window rate limiter.
//!
//! Kept simple: a `DashMap` of identities to a timestamp deque. The server
//! rejects any Send frame once the window is saturated. Memory usage is
//! proportional to `active_identities * max_per_window`.

use std::time::{Duration, Instant};

use dashmap::DashMap;
use harpo_proto::IdentityPubKey;

pub struct RateLimiter {
    window: Duration,
    limit: usize,
    inner: DashMap<IdentityPubKey, Vec<Instant>>,
}

impl RateLimiter {
    pub fn new(window: Duration, limit: usize) -> Self {
        Self {
            window,
            limit,
            inner: DashMap::new(),
        }
    }

    /// Record an event. Returns `true` if the caller is still within quota.
    pub fn check(&self, identity: &IdentityPubKey) -> bool {
        let now = Instant::now();
        let mut entry = self.inner.entry(*identity).or_default();
        // Drop expired timestamps from the front.
        entry.retain(|t| now.duration_since(*t) <= self.window);
        if entry.len() >= self.limit {
            return false;
        }
        entry.push(now);
        true
    }

    /// Number of tracked identities (for metrics).
    pub fn tracked(&self) -> usize {
        self.inner.len()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn within_quota() {
        let rl = RateLimiter::new(Duration::from_secs(60), 3);
        let id = [9u8; 32];
        assert!(rl.check(&id));
        assert!(rl.check(&id));
        assert!(rl.check(&id));
        assert!(!rl.check(&id));
    }

    #[test]
    fn per_identity_isolation() {
        let rl = RateLimiter::new(Duration::from_secs(60), 1);
        let a = [1u8; 32];
        let b = [2u8; 32];
        assert!(rl.check(&a));
        assert!(!rl.check(&a));
        // b has its own bucket
        assert!(rl.check(&b));
    }

    #[test]
    fn window_expiration_refills() {
        let rl = RateLimiter::new(Duration::from_millis(50), 1);
        let id = [7u8; 32];
        assert!(rl.check(&id));
        assert!(!rl.check(&id));
        std::thread::sleep(Duration::from_millis(70));
        assert!(rl.check(&id));
    }
}
