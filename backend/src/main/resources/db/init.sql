-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- ── Users ────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS users (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email       VARCHAR(255) NOT NULL UNIQUE,
    name        VARCHAR(255),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── Connected email accounts ──────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS email_accounts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider        VARCHAR(20) NOT NULL CHECK (provider IN ('GMAIL', 'MSGRAPH')),
    account_email   VARCHAR(255) NOT NULL,
    access_token    TEXT,
    refresh_token   TEXT,
    token_expiry    TIMESTAMPTZ,
    needs_reauth    BOOLEAN NOT NULL DEFAULT FALSE,
    history_backfill_before    TIMESTAMPTZ,
    history_backfill_complete  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, provider, account_email)
);

-- ── Agent sessions & messages ─────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS agent_sessions (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title             VARCHAR(255),
    summary           TEXT,
    compressed_until  INT NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_agent_sessions_user_id ON agent_sessions(user_id);

CREATE TABLE IF NOT EXISTS agent_messages (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id  UUID NOT NULL REFERENCES agent_sessions(id) ON DELETE CASCADE,
    role        VARCHAR(20) NOT NULL CHECK (role IN ('USER', 'ASSISTANT', 'TOOL')),
    content     TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_agent_messages_session_id ON agent_messages(session_id, created_at);

-- ── Email processing results ──────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS processing_results (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    version             BIGINT NOT NULL DEFAULT 0,
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    email_provider_id   VARCHAR(255) NOT NULL,
    account_id          UUID NOT NULL,
    subject             TEXT,
    sender              VARCHAR(500),
    category            VARCHAR(50),
    priority            VARCHAR(20),
    sentiment           VARCHAR(20),
    requires_response   BOOLEAN NOT NULL DEFAULT FALSE,
    action_taken        VARCHAR(20),
    summary             TEXT,
    action_items        TEXT,
    draft_status        VARCHAR(20),
    received_at         TIMESTAMPTZ,
    processed_at        TIMESTAMPTZ NOT NULL,
    analyzed            BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (user_id, account_id, email_provider_id)
);

CREATE INDEX IF NOT EXISTS idx_pr_user_id      ON processing_results(user_id);
CREATE INDEX IF NOT EXISTS idx_pr_received_at  ON processing_results(received_at DESC);
CREATE INDEX IF NOT EXISTS idx_pr_processed_at ON processing_results(processed_at DESC);
CREATE INDEX IF NOT EXISTS idx_pr_draft_status ON processing_results(draft_status);
-- Composite indexes for efficient keyset pagination
CREATE INDEX IF NOT EXISTS idx_pr_user_received ON processing_results(user_id, received_at DESC NULLS LAST, id ASC);
CREATE INDEX IF NOT EXISTS idx_pr_user_category ON processing_results(user_id, category, received_at DESC NULLS LAST, id ASC);
CREATE INDEX IF NOT EXISTS idx_pr_user_draft    ON processing_results(user_id, draft_status) WHERE draft_status IS NOT NULL;

-- ── Unified drafts (AI-generated + user-composed) ────────────────────────────

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

-- ── pgvector table for email content embeddings ───────────────────────────────

CREATE TABLE IF NOT EXISTS email_embeddings (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    source_id   VARCHAR(255) NOT NULL,
    content     TEXT NOT NULL,
    embedding   vector(1536),
    metadata    JSONB,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, source_id)
);

CREATE INDEX IF NOT EXISTS idx_email_embeddings_vector
    ON email_embeddings USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

-- ── Daily digest cache ────────────────────────────────────────────────────────

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
