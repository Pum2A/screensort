-- A snapshot of catalog entries, not a list of source files to move or rename.
CREATE TABLE analysis_batch (
    id UUID PRIMARY KEY,
    allowed_root_id BIGINT NOT NULL REFERENCES allowed_root(id),
    ocr_enabled BOOLEAN NOT NULL,
    visual_enabled BOOLEAN NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('RUNNING','PAUSED','COMPLETED','CANCELLED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (ocr_enabled OR visual_enabled)
);

-- This local application owns one model slot. A second batch must not create an unbounded queue.
CREATE UNIQUE INDEX ux_analysis_batch_active ON analysis_batch ((1)) WHERE status = 'RUNNING';

CREATE TABLE analysis_batch_item (
    batch_id UUID NOT NULL REFERENCES analysis_batch(id),
    file_id BIGINT NOT NULL REFERENCES file_record(id),
    source_sha256 VARCHAR(64) NOT NULL CHECK (source_sha256 ~ '^[0-9a-f]{64}$'),
    original_name VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','RUNNING','COMPLETED','FAILED')),
    analysis_id BIGINT REFERENCES file_analysis(id),
    category VARCHAR(40),
    proposed_name VARCHAR(255),
    message VARCHAR(500),
    PRIMARY KEY (batch_id, file_id)
);
CREATE INDEX ix_analysis_batch_pending ON analysis_batch_item(batch_id, file_id) WHERE status = 'PENDING';
