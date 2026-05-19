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
    reply_draft         TEXT,
    draft_status        VARCHAR(20),
    processed_at        TIMESTAMPTZ NOT NULL,
    UNIQUE (user_id, email_provider_id)
);

CREATE INDEX IF NOT EXISTS idx_pr_user_id      ON processing_results(user_id);
CREATE INDEX IF NOT EXISTS idx_pr_processed_at ON processing_results(processed_at DESC);
CREATE INDEX IF NOT EXISTS idx_pr_draft_status ON processing_results(draft_status);

-- ── pgvector table for email content embeddings ───────────────────────────────

CREATE TABLE IF NOT EXISTS email_embeddings (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    source_id   VARCHAR(255) NOT NULL,
    content     TEXT NOT NULL,
    embedding   vector(1536),
    metadata    JSONB,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_email_embeddings_vector
    ON email_embeddings USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
