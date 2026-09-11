-- MediKiosk M2 Phase 2: preserve the displayed question wording and its source.
--
-- Clinical records must keep both the canonical deterministic question/objective
-- and the wording actually shown to the patient (which a validated AI provider
-- may rephrase), plus where that wording came from. Existing rows are backfilled
-- to the canonical text / deterministic source.

ALTER TABLE completed_case_answers ADD COLUMN displayed_question_text VARCHAR(1000);
ALTER TABLE completed_case_answers ADD COLUMN question_source VARCHAR(16);

UPDATE completed_case_answers
   SET displayed_question_text = question_text
 WHERE displayed_question_text IS NULL;

UPDATE completed_case_answers
   SET question_source = 'DETERMINISTIC'
 WHERE question_source IS NULL;

ALTER TABLE completed_case_answers ALTER COLUMN displayed_question_text SET NOT NULL;
ALTER TABLE completed_case_answers ALTER COLUMN question_source SET NOT NULL;