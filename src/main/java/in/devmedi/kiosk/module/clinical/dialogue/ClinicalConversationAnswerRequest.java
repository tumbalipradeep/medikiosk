package in.devmedi.kiosk.module.clinical.dialogue;

/**
 * Payload submitted by the patient intake UI for one answer.
 *
 * @param questionId id of the question being answered
 * @param answer     the patient's free-text answer (not persisted in this checkpoint)
 */
public record ClinicalConversationAnswerRequest(String questionId, String answer) {
}