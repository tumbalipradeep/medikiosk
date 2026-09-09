package in.devmedi.kiosk.module.clinical.dialogue;

/**
 * One question in the clinical intake dialogue.
 *
 * <p>Questions carry a stable, code-unique id so the conversation position
 * can be tracked deterministically across turns.</p>
 *
 * @param id        stable question id (e.g. {@code hpi_onset})
 * @param section   clinical section this question belongs to
 * @param type      SOCRATES facet this question captures
 * @param text      question text shown to the patient
 * @param required  whether the answer is required (optional questions may be skipped)
 * @param order     deterministic ordering/priority within the dialogue
 */
public record ClinicalQuestion(String id,
                               ClinicalSection section,
                               QuestionType type,
                               String text,
                               boolean required,
                               int order) {
}