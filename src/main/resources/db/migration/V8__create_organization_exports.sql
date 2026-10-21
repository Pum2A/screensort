CREATE TABLE organization_export_job (
    id UUID PRIMARY KEY,
    batch_id UUID NOT NULL REFERENCES analysis_batch(id),
    output_folder VARCHAR(2048) NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('RUNNING', 'COMPLETED', 'INTERRUPTED')),
    message VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP WITH TIME ZONE
);
CREATE UNIQUE INDEX ux_organization_export_active ON organization_export_job ((1)) WHERE status = 'RUNNING';
CREATE INDEX ix_organization_export_latest ON organization_export_job (batch_id, created_at DESC);

-- Immutable approval snapshot. Copying does not hold a database transaction open.
CREATE TABLE organization_export_item (
    export_id UUID NOT NULL REFERENCES organization_export_job(id),
    file_id BIGINT NOT NULL REFERENCES file_record(id),
    source_folder VARCHAR(2048) NOT NULL,
    relative_path VARCHAR(2048) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes > 0),
    source_sha256 VARCHAR(64) NOT NULL CHECK (source_sha256 ~ '^[0-9a-f]{64}$'),
    approved_name VARCHAR(255) NOT NULL,
    category VARCHAR(40) NOT NULL CHECK (category IN ('PROGRAMMING','DOCUMENTS','GAMES','PHOTOGRAPHS','OTHER')),
    naming_revision BIGINT NOT NULL,
    review_revision BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','COPIED','FAILED')),
    target_relative_path VARCHAR(2048),
    message VARCHAR(255),
    PRIMARY KEY (export_id, file_id)
);
CREATE INDEX ix_organization_export_pending ON organization_export_item (export_id, file_id) WHERE status = 'PENDING';
