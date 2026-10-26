-- A name proposal built from weak evidence (only filler caption words, a single word) is shown
-- to the owner as uncertain. Rows written before this change had no such check: false.
ALTER TABLE analysis_batch_item
    ADD COLUMN proposal_uncertain BOOLEAN NOT NULL DEFAULT FALSE;
