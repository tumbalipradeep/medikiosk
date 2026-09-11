package in.devmedi.kiosk.ai;

import in.devmedi.kiosk.module.ai.conversation.AiQuestionMode;
import in.devmedi.kiosk.module.ai.conversation.AiQuestionParseException;
import in.devmedi.kiosk.module.ai.conversation.AiQuestionResponse;
import in.devmedi.kiosk.module.ai.conversation.AiRejectionReason;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiQuestionResponseParsingTests {

    @Test
    void parsesCompleteQuestionObject() {
        AiQuestionResponse response = AiQuestionResponse.fromJson(
                "{\"mode\":\"QUESTION\",\"question\":\"When did it first start?\",\"topic\":\"ONSET\",\"continue\":true}");

        assertThat(response.mode()).isEqualTo(AiQuestionMode.QUESTION);
        assertThat(response.question()).isEqualTo("When did it first start?");
        assertThat(response.followUpTopic()).isEqualTo("ONSET");
        assertThat(response.continueConversation()).isTrue();
    }

    @Test
    void parsesClarificationObject() {
        AiQuestionResponse response = AiQuestionResponse.fromJson(
                "{\"mode\":\"CLARIFICATION\",\"question\":\"Could you tell me more about the dull ache?\",\"topic\":\"\"}");

        assertThat(response.mode()).isEqualTo(AiQuestionMode.CLARIFICATION);
        assertThat(response.question()).isEqualTo("Could you tell me more about the dull ache?");
    }

    @Test
    void parsesCanonicalObjectWithoutQuestion() {
        AiQuestionResponse response = AiQuestionResponse.fromJson("{\"mode\":\"CANONICAL\"}");

        assertThat(response.mode()).isEqualTo(AiQuestionMode.CANONICAL);
        assertThat(response.question()).isNull();
    }

    @Test
    void parsesModeCaseInsensitively() {
        AiQuestionResponse response = AiQuestionResponse.fromJson(
                "{\"mode\":\"clarification\",\"question\":\"Anything else about that?\"}");

        assertThat(response.mode()).isEqualTo(AiQuestionMode.CLARIFICATION);
    }

    @Test
    void parsesObjectWithoutOptionalFields() {
        AiQuestionResponse response = AiQuestionResponse.fromJson(
                "{\"mode\":\"QUESTION\",\"question\":\"How does that feel?\"}");

        assertThat(response.question()).isEqualTo("How does that feel?");
        assertThat(response.followUpTopic()).isNull();
        assertThat(response.continueConversation()).isNull();
    }

    @Test
    void parsesExplicitNullTopicAndNegativeContinue() {
        AiQuestionResponse response = AiQuestionResponse.fromJson(
                "{\"mode\":\"QUESTION\",\"question\":\"Anything else?\",\"topic\":null,\"continue\":false}");

        assertThat(response.followUpTopic()).isNull();
        assertThat(response.continueConversation()).isFalse();
    }

    @Test
    void ignoresUnexpectedExtraFields() {
        AiQuestionResponse response = AiQuestionResponse.fromJson(
                "{\"mode\":\"QUESTION\",\"question\":\"Where exactly?\",\"topic\":\"REGION_RADIATION\",\"extra\":{\"nested\":true}}");

        assertThat(response.question()).isEqualTo("Where exactly?");
        assertThat(response.followUpTopic()).isEqualTo("REGION_RADIATION");
    }

    @Test
    void blankContentIsRejected() {
        assertThatThrownBy(() -> AiQuestionResponse.fromJson("  "))
                .isInstanceOf(AiQuestionParseException.class)
                .extracting(ex -> ((AiQuestionParseException) ex).getReason())
                .isEqualTo(AiRejectionReason.EMPTY_COMPLETION);
    }

    @Test
    void nonJsonContentIsRejected() {
        assertThatThrownBy(() -> AiQuestionResponse.fromJson("I am not json, please help"))
                .isInstanceOf(AiQuestionParseException.class)
                .extracting(ex -> ((AiQuestionParseException) ex).getReason())
                .isEqualTo(AiRejectionReason.INVALID_JSON);
    }

    @Test
    void jsonArrayIsRejected() {
        assertThatThrownBy(() -> AiQuestionResponse.fromJson("[1,2,3]"))
                .isInstanceOf(AiQuestionParseException.class)
                .extracting(ex -> ((AiQuestionParseException) ex).getReason())
                .isEqualTo(AiRejectionReason.NON_OBJECT_OUTPUT);
    }

    @Test
    void jsonStringIsRejected() {
        assertThatThrownBy(() -> AiQuestionResponse.fromJson("\"just a string\""))
                .isInstanceOf(AiQuestionParseException.class)
                .extracting(ex -> ((AiQuestionParseException) ex).getReason())
                .isEqualTo(AiRejectionReason.NON_OBJECT_OUTPUT);
    }

    @Test
    void missingModeIsRejected() {
        assertThatThrownBy(() -> AiQuestionResponse.fromJson("{\"question\":\"When did it start?\"}"))
                .isInstanceOf(AiQuestionParseException.class)
                .extracting(ex -> ((AiQuestionParseException) ex).getReason())
                .isEqualTo(AiRejectionReason.MODE_MISSING_OR_INVALID);
    }

    @Test
    void nonTextModeIsRejected() {
        assertThatThrownBy(() -> AiQuestionResponse.fromJson("{\"mode\":3,\"question\":\"When did it start?\"}"))
                .isInstanceOf(AiQuestionParseException.class)
                .extracting(ex -> ((AiQuestionParseException) ex).getReason())
                .isEqualTo(AiRejectionReason.INVALID_MODE_TYPE);
    }

    @Test
    void unknownModeValueIsRejected() {
        assertThatThrownBy(() -> AiQuestionResponse.fromJson("{\"mode\":\"DIAGNOSIS\",\"question\":\"When did it start?\"}"))
                .isInstanceOf(AiQuestionParseException.class)
                .extracting(ex -> ((AiQuestionParseException) ex).getReason())
                .isEqualTo(AiRejectionReason.MODE_MISSING_OR_INVALID);
    }

    @Test
    void missingQuestionIsRejectedForQuestionMode() {
        assertThatThrownBy(() -> AiQuestionResponse.fromJson("{\"mode\":\"QUESTION\",\"topic\":\"ONSET\"}"))
                .isInstanceOf(AiQuestionParseException.class)
                .extracting(ex -> ((AiQuestionParseException) ex).getReason())
                .isEqualTo(AiRejectionReason.QUESTION_MISSING_OR_INVALID);
    }

    @Test
    void nonTextQuestionIsRejected() {
        assertThatThrownBy(() -> AiQuestionResponse.fromJson("{\"mode\":\"QUESTION\",\"question\":42}"))
                .isInstanceOf(AiQuestionParseException.class)
                .extracting(ex -> ((AiQuestionParseException) ex).getReason())
                .isEqualTo(AiRejectionReason.QUESTION_MISSING_OR_INVALID);
    }

    @Test
    void stringContinueSignalsAreRejected() {
        assertThatThrownBy(() -> AiQuestionResponse.fromJson(
                "{\"mode\":\"QUESTION\",\"question\":\"Anything else?\",\"continue\":\"true\"}"))
                .isInstanceOf(AiQuestionParseException.class)
                .extracting(ex -> ((AiQuestionParseException) ex).getReason())
                .isEqualTo(AiRejectionReason.INVALID_CONTINUE_SIGNAL);
    }

    @Test
    void nonTextTopicIsRejected() {
        assertThatThrownBy(() -> AiQuestionResponse.fromJson(
                "{\"mode\":\"QUESTION\",\"question\":\"Anything else?\",\"topic\":5}"))
                .isInstanceOf(AiQuestionParseException.class)
                .extracting(ex -> ((AiQuestionParseException) ex).getReason())
                .isEqualTo(AiRejectionReason.INVALID_TOPIC_TYPE);
    }
}