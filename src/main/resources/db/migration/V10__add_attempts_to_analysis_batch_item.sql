-- A file whose analysis timed out goes to the end of the queue and gets one more, longer attempt.
-- The worker takes pending items by attempts first, so retried files come after the fresh ones.
ALTER TABLE analysis_batch_item
    ADD COLUMN attempts SMALLINT NOT NULL DEFAULT 0 CHECK (attempts >= 0);

DROP INDEX IF EXISTS ix_analysis_batch_pending;
CREATE INDEX ix_analysis_batch_pending ON analysis_batch_item(batch_id, attempts, file_id) WHERE status = 'PENDING';
