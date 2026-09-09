package in.devmedi.kiosk.module.ai.conversation;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable request sent to an AI provider for the clinical conversation.
 * Carrier of conversation data only — it carries no clinical interpretation.
 */
public final class ClinicalAiRequest {

    private final String language;
    private final String currentQuestion;
    private final String patientAnswer;
    private final List<ConversationTurn> conversation;
    private final String requestedResponse;

    public ClinicalAiRequest(String language,
                             String currentQuestion,
                             String patientAnswer,
                             List<ConversationTurn> conversation,
                             String requestedResponse) {
        this.language = language;
        this.currentQuestion = currentQuestion;
        this.patientAnswer = patientAnswer;
        this.conversation = conversation == null ? List.of() : List.copyOf(conversation);
        this.requestedResponse = requestedResponse;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getLanguage() {
        return language;
    }

    public String getCurrentQuestion() {
        return currentQuestion;
    }

    public String getPatientAnswer() {
        return patientAnswer;
    }

    public List<ConversationTurn> getConversation() {
        return conversation;
    }

    public String getRequestedResponse() {
        return requestedResponse;
    }

    public static final class Builder {
        private String language;
        private String currentQuestion;
        private String patientAnswer;
        private final List<ConversationTurn> conversation = new ArrayList<>();
        private String requestedResponse;

        public Builder language(String language) {
            this.language = language;
            return this;
        }

        public Builder currentQuestion(String currentQuestion) {
            this.currentQuestion = currentQuestion;
            return this;
        }

        public Builder patientAnswer(String patientAnswer) {
            this.patientAnswer = patientAnswer;
            return this;
        }

        public Builder turn(ConversationTurn turn) {
            this.conversation.add(turn);
            return this;
        }

        public Builder conversation(List<ConversationTurn> turns) {
            this.conversation.addAll(turns);
            return this;
        }

        public Builder requestedResponse(String requestedResponse) {
            this.requestedResponse = requestedResponse;
            return this;
        }

        public ClinicalAiRequest build() {
            return new ClinicalAiRequest(language, currentQuestion, patientAnswer, conversation, requestedResponse);
        }
    }
}