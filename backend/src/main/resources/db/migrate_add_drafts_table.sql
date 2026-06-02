-- Add unified drafts table for AI-generated and user-composed drafts.
-- Run once against existing databases.

CREATE TABLE IF NOT EXISTS drafts (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    version               BIGINT NOT NULL DEFAULT 0,
    user_id               UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    account_id            UUID NOT NULL REFERENCES email_accounts(id) ON DELETE CASCADE,
    source                VARCHAR(10) NOT NULL CHECK (source IN ('AI', 'USER')),
    status                VARCHAR(20) NOT NULL DEFAULT 'PENDING_REVIEW'
                              CHECK (status IN ('PENDING_REVIEW', 'SENT', 'REJECTED')),
    to_addresses          TEXT NOT NULL,
    subject               TEXT,
    body                  TEXT NOT NULL DEFAULT '',
    reply_to_provider_id  VARCHAR(255),
    result_id             UUID REFERENCES processing_results(id) ON DELETE SET NULL,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_drafts_user_status ON drafts(user_id, status, created_at DESC);
