-- Add per-account historical backfill cursor for on-demand "load older emails".
-- Run once against existing databases.

ALTER TABLE email_accounts
    ADD COLUMN IF NOT EXISTS history_backfill_before    TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS history_backfill_complete  BOOLEAN NOT NULL DEFAULT FALSE;
