package in.devmedi.kiosk.module.ai.conversation;

/**
 * Intent of one structured AI question response.
 *
 * <p>The model never decides <em>what</em> to collect — only <em>how</em> to
 * phrase it within the deterministic objective. These three intents are the
 * only ways the wording slot may be filled, and every one of them is validated
 * before it can be shown to the patient.</p>
 */
public enum AiQuestionMode {

    /**
     * A natural, conversational phrasing of the current deterministic next
     * question, adapted to the ongoing conversation.
     */
    QUESTION,

    /**
     * A short follow-up asking the patient to be more specific about their
     * latest answer. Must still stay inside the current objective.
     */
    CLARIFICATION,

    /**
     * Keep the canonical deterministic question text exactly as-is; no better
     * wording is needed.
     */
    CANONICAL
}