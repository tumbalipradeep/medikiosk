package in.devmedi.kiosk.module.clinical.ai;

/**
 * Where the question text ultimately came from.
 *
 * <p>{@link #DETERMINISTIC_FALLBACK} is the safe default and the only behavior
 * observed when no provider is configured or everything fails.</p>
 */
public enum NextQuestionSource {

    /** The text was produced by a validated AI provider. */
    AI_GENERATED,

    /** The text is the canonical deterministic question. */
    DETERMINISTIC_FALLBACK
}