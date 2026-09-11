package in.devmedi.kiosk.module.ai.conversation;

/**
 * Safer rejection reasons produced when structured AI question output cannot be
 * trusted. Used both while parsing a provider completion into an
 * {@link AiQuestionResponse} and while validating it for clinical safety.
 */
public enum AiRejectionReason {

    /** Empty provider completion (no content text). */
    EMPTY_COMPLETION,

    /** Content was not valid JSON. */
    INVALID_JSON,

    /** Valid JSON but not a single JSON object. */
    NON_OBJECT_OUTPUT,

    /** No usable {@code question} text field in the payload. */
    QUESTION_MISSING_OR_INVALID,

    /** {@code topic} present but not a plain text value. */
    INVALID_TOPIC_TYPE,

    /** {@code continue} present but not a boolean value. */
    INVALID_CONTINUE_SIGNAL,

    /** Question text is empty or blank. */
    BLANK_QUESTION,

    /** Question text is shorter than the safety floor. */
    QUESTION_TOO_SHORT,

    /** Question text exceeds the rendering safety ceiling. */
    QUESTION_TOO_LONG,

    /** Output does not read as a follow-up question or request for information. */
    NOT_QUESTION_LIKE,

    /** Output asserts clinical facts, advice, or inventions the model may not claim. */
    FORBIDDEN_CLINICAL_CLAIM,

    /** Follow-up topic escapes the allowed topics of the current deterministic section. */
    TOPIC_OUT_OF_SECTION,

    /** Follow-up topic restates an objective that has already been covered. */
    TOPIC_ALREADY_COVERED,

    /** Question repeats a question that has already been asked or answered. */
    DUPLICATE_QUESTION,

    /** Question leaves the bounds of the current deterministic clinical objective. */
    OBJECTIVE_OUT_OF_BOUNDS,

    /** {@code mode} present but not a plain text value. */
    INVALID_MODE_TYPE,

    /** {@code mode} missing, blank, or holds an unknown intent. */
    MODE_MISSING_OR_INVALID
}