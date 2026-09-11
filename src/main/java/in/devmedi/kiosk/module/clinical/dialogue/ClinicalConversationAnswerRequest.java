package in.devmedi.kiosk.module.clinical.dialogue;

/**
 * Payload submitted by the patient intake UI for one answer.
 *
 * <p>The {@link #answer()} is always the patient's raw text — whether it was
 * typed or produced by speech recognition. The optional {@link #answerSource()}
 * only records the entry method; it never changes the clinical answer or how
 * the answer is processed.</p>
 *
 * @param questionId   id of the question being answered
 * @param answer       the patient's free-text answer (verbatim, source of truth)
 * @param answerSource how the answer entered the pipeline ({@code TEXT} or
 *                     {@code VOICE}); {@code null}/blank/unknown defaults to {@code TEXT}
 */
public record ClinicalConversationAnswerRequest(String questionId, String answer, String answerSource) {

    public ClinicalConversationAnswerRequest(String questionId, String answer) {
        this(questionId, answer, null);
    }
}