-- MediKiosk M3 Phase 2: record the patient-facing language captured for each answer.
--
-- language is presentation-layer metadata (BCP-47, e.g. 'en-IN', 'hi-IN',
-- 'te-IN') documenting in which patient-facing language the intake step was
-- answered. It never influences clinical content. Existing rows predate
-- language capture, so every answer is backfilled to the deterministic default,
-- English.

ALTER TABLE completed_case_answers ADD COLUMN answer_language VARCHAR(16);

UPDATE completed_case_answers
   SET answer_language = 'en-IN'
 WHERE answer_language IS NULL;

ALTER TABLE completed_case_answers ALTER COLUMN answer_language SET NOT NULL;