-- Envelopes queued for offline recipients. `to_identity` is indexed because
-- delivery flushes by recipient. `id` is a ULID-like v4 UUID as text.
CREATE TABLE IF NOT EXISTS pending_envelopes (
    id            TEXT PRIMARY KEY NOT NULL,
    to_identity   BLOB NOT NULL,
    from_identity BLOB NOT NULL,
    ciphertext    BLOB NOT NULL,
    signature     BLOB NOT NULL,
    ts_ms         INTEGER NOT NULL,
    received_at   INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_pending_to_received
    ON pending_envelopes (to_identity, received_at);
