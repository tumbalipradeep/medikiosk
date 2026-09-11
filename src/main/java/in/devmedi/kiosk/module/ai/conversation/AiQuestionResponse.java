package in.devmedi.kiosk.module.ai.conversation;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Locale;

/**
 * Structured, minimal AI output for the next question of the clinical
 * conversation. Providers are asked to reply with exactly this schema:
 *
 * <pre>{@code
 * {
 *   "mode": "QUESTION" | "CLARIFICATION" | "CANONICAL",
 *   "question": "single follow-up question (required for QUESTION/CLARIFICATION)",
 *   "topic": "optional allowed topic",
 *   "continue": bool
 * }
 * }</pre>
 *
 * <p>Parsing is strict by design: the {@code mode} must always be present and
 * hold one of the three intents, {@code question} must be present and textual
 * for the {@code QUESTION}/{@code CLARIFICATION} intents, {@code topic} (when
 * present) must be text, and {@code continue} (when present) must be an actual
 * boolean value. The strictness protects the conversation from malformed model
 * output — anything unexpected becomes an {@link AiRejectionReason} and the
 * deterministic backbone takes over.</p>
 *
 * @param mode                 the intent of this response
 * @param question             the single follow-up question text (ignored for {@code CANONICAL})
 * @param followUpTopic        optional topic/category of the follow-up ({@code null} or blank when unused)
 * @param continueConversation whether enough has been collected to move on (informational; progression stays deterministic)
 */
public record AiQuestionResponse(AiQuestionMode mode,
                                 String question,
                                 String followUpTopic,
                                 Boolean continueConversation) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static AiQuestionResponse of(AiQuestionMode mode,
                                        String question,
                                        String followUpTopic,
                                        Boolean continueConversation) {
        return new AiQuestionResponse(mode == null ? AiQuestionMode.QUESTION : mode,
                question, followUpTopic, continueConversation);
    }

    /** Convenience for question-mode responses. */
    public static AiQuestionResponse of(String question, String followUpTopic, Boolean continueConversation) {
        return AiQuestionResponse.of(AiQuestionMode.QUESTION, question, followUpTopic, continueConversation);
    }

    /**
     * Strictly decodes a provider completion into an {@link AiQuestionResponse}.
     *
     * @param content raw provider completion text
     * @return the decoded response
     * @throws AiQuestionParseException with a safe {@link AiRejectionReason} when the
     *                                  payload is malformed or type-invalid
     */
    public static AiQuestionResponse fromJson(String content) {
        if (content == null || content.isBlank()) {
            throw new AiQuestionParseException(AiRejectionReason.EMPTY_COMPLETION, "empty provider completion");
        }
        final JsonNode node;
        try {
            node = MAPPER.readTree(content);
        } catch (tools.jackson.core.JacksonException ex) {
            throw new AiQuestionParseException(AiRejectionReason.INVALID_JSON, "provider output is not valid JSON", ex);
        }
        if (node == null || !node.isObject()) {
            throw new AiQuestionParseException(AiRejectionReason.NON_OBJECT_OUTPUT, "provider output is not a JSON object");
        }

        AiQuestionMode mode = parseMode(node);

        String question = null;
        if (mode != AiQuestionMode.CANONICAL) {
            JsonNode questionNode = node.get("question");
            if (questionNode == null || !questionNode.isTextual()) {
                throw new AiQuestionParseException(AiRejectionReason.QUESTION_MISSING_OR_INVALID,
                        "question field missing or not text");
            }
            question = questionNode.asText();
        }

        JsonNode topicNode = node.get("topic");
        String topic = null;
        if (topicNode != null && !topicNode.isNull()) {
            if (!topicNode.isTextual()) {
                throw new AiQuestionParseException(AiRejectionReason.INVALID_TOPIC_TYPE, "topic field is not text");
            }
            topic = topicNode.asText();
        }

        JsonNode continueNode = node.get("continue");
        Boolean continueConversation = null;
        if (continueNode != null && !continueNode.isNull()) {
            if (!continueNode.isBoolean()) {
                throw new AiQuestionParseException(AiRejectionReason.INVALID_CONTINUE_SIGNAL, "continue field is not a boolean");
            }
            continueConversation = continueNode.asBoolean();
        }

        return new AiQuestionResponse(mode, question, topic, continueConversation);
    }

    private static AiQuestionMode parseMode(JsonNode node) {
        JsonNode modeNode = node.get("mode");
        if (modeNode == null || modeNode.isNull()) {
            throw new AiQuestionParseException(AiRejectionReason.MODE_MISSING_OR_INVALID, "mode field is required");
        }
        if (!modeNode.isTextual()) {
            throw new AiQuestionParseException(AiRejectionReason.INVALID_MODE_TYPE, "mode field is not text");
        }
        try {
            return AiQuestionMode.valueOf(modeNode.asText().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new AiQuestionParseException(AiRejectionReason.MODE_MISSING_OR_INVALID, "mode holds an unknown intent");
        }
    }
}