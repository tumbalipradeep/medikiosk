-- M1.4: Deterministic interpretation layer over document findings.
--
-- Extends the existing findings tables with the outcome of the analysis
-- pass: lab values compared against the document's own reference range,
-- medication completeness/duplicate flags, and the interaction-analysis
-- availability marker. All columns are nullable-friendly and default to a
-- safe value so existing rows remain valid (idempotent regeneration of the
-- findings row for an extraction re-writes them).

ALTER TABLE clinical_document_findings
    ADD COLUMN IF NOT EXISTS interaction_analysis_status VARCHAR(32) NOT NULL DEFAULT 'NOT_AVAILABLE';

ALTER TABLE clinical_document_findings_labs
    ADD COLUMN IF NOT EXISTS raw_value VARCHAR(50);

ALTER TABLE clinical_document_findings_labs
    ADD COLUMN IF NOT EXISTS abnormality_status VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN';

ALTER TABLE clinical_document_findings_medications
    ADD COLUMN IF NOT EXISTS completeness_status VARCHAR(16) NOT NULL DEFAULT 'INCOMPLETE';

ALTER TABLE clinical_document_findings_medications
    ADD COLUMN IF NOT EXISTS missing_fields VARCHAR(120);

ALTER TABLE clinical_document_findings_medications
    ADD COLUMN IF NOT EXISTS duplicate_count INTEGER NOT NULL DEFAULT 1;