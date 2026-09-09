package in.devmedi.kiosk.module.clinical.dialogue;

/**
 * A single structured clinical intake answer.
 *
 * <p>Captures what the patient was asked and what they answered, with enough
 * metadata to identify the question and its AYUSH/clinical section without
 * re-querying a planner. Built from the render-ready {@link IntakeQuestion}
 * that was shown to the patient.</p>
 *
 * @param questionId   stable question id (e.g. {@code hpi_onset} or {@code dashavidha_sara})
 * @param section      section the question belongs to (HPI section, DASHAVIDHA, or AHARA_VIHARA)
 * @param questionType question type: SOCRATES facet or AYUSH parameter name
 * @param questionText the question text shown to the patient
 * @param answer       the patient's free-text answer
 */
public record ClinicalAnswer(String questionId,
                             String section,
                             String questionType,
                             String questionText,
                             String answer) {

    public static ClinicalAnswer from(IntakeQuestion question, String answer) {
        return new ClinicalAnswer(question.id(),
                question.section(),
                question.type(),
                question.text(),
                answer);
    }
}