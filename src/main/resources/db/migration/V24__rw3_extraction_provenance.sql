-- RW3: extractor provenance on clinical document extractions.
--
-- Records HOW text was extracted (PDF text layer vs an OCR engine) and WHICH
-- engine produced it, so provenance survives persistence and the UI can show it.
-- All columns are nullable: rows written before this migration have no recorded
-- provenance and extraction runs that cannot classify themselves store NULL.

ALTER TABLE clinical_document_extractions
    ADD COLUMN IF NOT EXISTS extraction_method VARCHAR(24);

ALTER TABLE clinical_document_extractions
    ADD COLUMN IF NOT EXISTS provider_name VARCHAR(60);

ALTER TABLE clinical_document_extractions
    ADD COLUMN IF NOT EXISTS source_language VARCHAR(8);