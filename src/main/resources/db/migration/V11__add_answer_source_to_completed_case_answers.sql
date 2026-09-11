-- MediKiosk M3 Phase 1: record how each patient answer entered the pipeline.
--
-- The patient answer itself is always the raw text and is never derived from
-- this column. answer_source is presentation-layer metadata (TEXT or VOICE)
-- that documents whether the answer was typed or voice-transcribed. Existing
-- rows predate voice input, so every answer is backfilled to TEXT.

ALTER TABLE completed_case_answers ADD COLUMN answer_source VARCHAR(8);

UPDATE completed_case_answers
   SET answer_source = 'TEXT'
 WHERE answer_source IS NULL;

ALTER TABLE completed_case_answers ALTER COLUMN answer_source SET NOT NULL;