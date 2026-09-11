package in.devmedi.kiosk.module.ai.conversation;

/**
 * Result of validating a structured AI question response. An invalid response
 * is never shown to the patient; the deterministic clinical backbone supplies
 * the fallback question instead.
 *
 * @param valid   whether the AI question may be accepted
 * @param reason  rejection reason when {@code valid} is false (may be {@code null} for valid results)
 * @param message human-readable detail for logs (never patient content)
 */
public record AiQuestionValidation(boolean valid, AiRejectionReason reason, String message) {

    public static AiQuestionValidation accepted() {
        return new AiQuestionValidation(true, null, null);
    }

    public static AiQuestionValidation rejected(AiRejectionReason reason, String message) {
        return new AiQuestionValidation(false, reason, message);
    }
}