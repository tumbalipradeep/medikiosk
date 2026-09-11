package in.devmedi.kiosk.module.ai.conversation;

import java.util.List;

/**
 * Provider-neutral context for one AI question-generation step. Carries the
 * minimum structured information an LLM needs — never the whole domain model:
 *
 * <ul>
 *   <li>current deterministic clinical section</li>
 *   <li>the deterministic next question (the allowed objective the AI may phrase)</li>
 *   <li>the question the patient just answered and their latest verbatim answer</li>
 *   <li>a few relevant previously collected question/answer pairs</li>
 *   <li>every objectively already-asked question and covered topic, so the AI does not repeat them</li>
 *   <li>the follow-up topics the current step permits</li>
 * </ul>
 *
 * @param language              ISO language code for the patient-facing question
 * @param section               current deterministic clinical section name
 * @param questionTopic         facet/parameter the deterministic step is collecting (the allowed objective)
 * @param targetQuestionText    canonical deterministic next-question text (the only objective the AI may phrase)
 * @param currentQuestionText   canonical text of the question the patient just answered (may be {@code null})
 * @param currentQuestionTopic  topic of the question the patient just answered (may be {@code null})
 * @param patientAnswer         the patient's latest verbatim answer (source of truth)
 * @param previousTurns         recent, relevant previous question/answer pairs excluding the current one (compact)
 * @param allowedFollowUpTopics topic tokens the current deterministic step permits
 * @param alreadyAskedQuestions canonical texts of every question already asked (deduplicated, may be empty)
 * @param askedTopics           every objective topic already covered (deduplicated, may be empty)
 */
public record AiQuestionRequest(String language,
                                String section,
                                String questionTopic,
                                String targetQuestionText,
                                String currentQuestionText,
                                String currentQuestionTopic,
                                String patientAnswer,
                                List<ConversationTurn> previousTurns,
                                List<String> allowedFollowUpTopics,
                                List<String> alreadyAskedQuestions,
                                List<String> askedTopics) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String language;
        private String section;
        private String questionTopic;
        private String targetQuestionText;
        private String currentQuestionText;
        private String currentQuestionTopic;
        private String patientAnswer;
        private List<ConversationTurn> previousTurns = List.of();
        private List<String> allowedFollowUpTopics = List.of();
        private List<String> alreadyAskedQuestions = List.of();
        private List<String> askedTopics = List.of();

        public Builder language(String language) {
            this.language = language;
            return this;
        }

        public Builder section(String section) {
            this.section = section;
            return this;
        }

        public Builder questionTopic(String questionTopic) {
            this.questionTopic = questionTopic;
            return this;
        }

        public Builder targetQuestionText(String targetQuestionText) {
            this.targetQuestionText = targetQuestionText;
            return this;
        }

        public Builder currentQuestionText(String currentQuestionText) {
            this.currentQuestionText = currentQuestionText;
            return this;
        }

        public Builder currentQuestionTopic(String currentQuestionTopic) {
            this.currentQuestionTopic = currentQuestionTopic;
            return this;
        }

        public Builder patientAnswer(String patientAnswer) {
            this.patientAnswer = patientAnswer;
            return this;
        }

        public Builder previousTurns(List<ConversationTurn> previousTurns) {
            this.previousTurns = previousTurns == null ? List.of() : List.copyOf(previousTurns);
            return this;
        }

        public Builder allowedFollowUpTopics(List<String> allowedFollowUpTopics) {
            this.allowedFollowUpTopics = allowedFollowUpTopics == null ? List.of() : List.copyOf(allowedFollowUpTopics);
            return this;
        }

        public Builder alreadyAskedQuestions(List<String> alreadyAskedQuestions) {
            this.alreadyAskedQuestions = alreadyAskedQuestions == null ? List.of() : List.copyOf(alreadyAskedQuestions);
            return this;
        }

        public Builder askedTopics(List<String> askedTopics) {
            this.askedTopics = askedTopics == null ? List.of() : List.copyOf(askedTopics);
            return this;
        }

        public AiQuestionRequest build() {
            return new AiQuestionRequest(language, section, questionTopic, targetQuestionText,
                    currentQuestionText, currentQuestionTopic, patientAnswer, previousTurns,
                    allowedFollowUpTopics, alreadyAskedQuestions, askedTopics);
        }
    }
}