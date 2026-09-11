package in.devmedi.kiosk.module.clinical.dialogue;

/**
 * Where the question text shown to the patient came from.
 *
 * <p>{@link #DETERMINISTIC} is the safe default and means the canonical planner
 * text was shown verbatim. {@link #AI_GENERATED} means a validated AI provider
 * supplied the wording. Either way the canonical question text and objective
 * are preserved unchanged in the {@link ClinicalAnswer}; only the displayed
 * wording may differ.</p>
 */
public enum QuestionSource {

    /** The canonical deterministic question text was shown. */
    DETERMINISTIC,

    /** A validated AI provider supplied the displayed wording. */
    AI_GENERATED
}