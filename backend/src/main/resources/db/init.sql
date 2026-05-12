-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Users
CREATE TABLE IF NOT EXISTS users (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email       VARCHAR(255) NOT NULL UNIQUE,
    name        VARCHAR(255),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Connected email accounts per user
CREATE TABLE IF NOT EXISTS email_accounts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider        VARCHAR(20) NOT NULL CHECK (provider IN ('GMAIL', 'MSGRAPH')),
    account_email   VARCHAR(255) NOT NULL,
    access_token    TEXT,
    refresh_token   TEXT,
    token_expiry    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, provider, account_email)
);

-- Raw emails (metadata only, body in vector store)
CREATE TABLE IF NOT EXISTS emails (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id      UUID NOT NULL REFERENCES email_accounts(id) ON DELETE CASCADE,
    provider_id     VARCHAR(255) NOT NULL,
    subject         VARCHAR(1000),
    sender          VARCHAR(500),
    recipients      TEXT[],
    received_at     TIMESTAMPTZ,
    category        VARCHAR(50),
    priority        VARCHAR(20),
    sentiment       VARCHAR(20),
    summary         TEXT,
    is_read         BOOLEAN DEFAULT FALSE,
    is_archived     BOOLEAN DEFAULT FALSE,
    is_flagged      BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (account_id, provider_id)
);

CREATE INDEX IF NOT EXISTS idx_emails_account_received ON emails(account_id, received_at DESC);
CREATE INDEX IF NOT EXISTS idx_emails_category ON emails(category);

-- pgvector table for email content embeddings
CREATE TABLE IF NOT EXISTS email_embeddings (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email_id    UUID NOT NULL REFERENCES emails(id) ON DELETE CASCADE,
    content     TEXT NOT NULL,
    embedding   vector(1536),
    metadata    JSONB,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_email_embeddings_vector
    ON email_embeddings USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

-- Agent run logs
CREATE TABLE IF NOT EXISTS agent_runs (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID REFERENCES users(id),
    run_type    VARCHAR(50) NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    started_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    finished_at TIMESTAMPTZ,
    metadata    JSONB
);