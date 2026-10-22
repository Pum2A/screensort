-- Claiming a file for copying needs its own state, so two workers cannot take the same row.
-- The exporter marks an item RUNNING in the same transaction that selects it.
ALTER TABLE organization_export_item
    DROP CONSTRAINT IF EXISTS organization_export_item_status_check;

ALTER TABLE organization_export_item
    ADD CONSTRAINT organization_export_item_status_check
    CHECK (status IN ('PENDING','RUNNING','COPIED','FAILED'));
