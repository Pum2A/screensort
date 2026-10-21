-- A user's decision is not a model prediction and does not rename the source file.
-- The approved filename remains in file_naming; its revision binds the two decisions.
CREATE TABLE file_organization_review (
    file_record_id BIGINT PRIMARY KEY REFERENCES file_record(id),
    analysis_id BIGINT NOT NULL REFERENCES file_analysis(id),
    source_sha256 VARCHAR(64) NOT NULL CHECK (source_sha256 ~ '^[0-9a-f]{64}$'),
    decision VARCHAR(16) NOT NULL CHECK (decision IN ('APPROVED', 'SKIPPED')),
    category VARCHAR(40),
    naming_revision BIGINT,
    reviewed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0 CHECK (revision >= 0),
    CONSTRAINT ck_organization_review_decision CHECK (
        (decision = 'APPROVED' AND category IS NOT NULL
            AND category IN ('PROGRAMMING', 'DOCUMENTS', 'GAMES', 'PHOTOGRAPHS', 'OTHER')
            AND naming_revision IS NOT NULL AND naming_revision >= 0)
        OR (decision = 'SKIPPED' AND category IS NULL AND naming_revision IS NULL)
    )
);
