-- Add composite indexes for efficient keyset pagination on processing_results.
-- Run once against existing databases (CONCURRENTLY avoids table lock).

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_pr_user_received
    ON processing_results(user_id, received_at DESC NULLS LAST, id ASC);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_pr_user_category
    ON processing_results(user_id, category, received_at DESC NULLS LAST, id ASC);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_pr_user_draft
    ON processing_results(user_id, draft_status)
    WHERE draft_status IS NOT NULL;
