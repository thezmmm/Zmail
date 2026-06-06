-- Add daily_digests table for storing per-user per-day email digests.
-- Run once against existing databases.

CREATE TABLE IF NOT EXISTS daily_digests (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    digest_date  DATE NOT NULL,
    overview     TEXT,
    emails       TEXT,
    priorities   TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, digest_date)
);

CREATE INDEX IF NOT EXISTS idx_daily_digests_user_date ON daily_digests(user_id, digest_date DESC);
