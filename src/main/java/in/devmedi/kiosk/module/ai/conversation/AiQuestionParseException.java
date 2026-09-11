package in.devmedi.kiosk.module.ai.conversation;

/**
 * Raised when a provider completion cannot be decoded into a well-formed
 * {@link AiQuestionResponse}. Structural problems carry a safe, human-readable
 * {@link AiRejectionReason}; the raw provider text is never retained here.
 */
public final class AiQuestionParseException extends RuntimeException {

    private final AiRejectionReason reason;

    public AiQuestionParseException(AiRejectionReason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public AiQuestionParseException(AiRejectionReason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public AiRejectionReason getReason() {
        return reason;
    }
}