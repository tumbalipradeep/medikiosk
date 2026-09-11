package in.devmedi.kiosk.ai;

import in.devmedi.kiosk.module.ai.conversation.AiQuestionMode;
import in.devmedi.kiosk.module.ai.conversation.AiQuestionResponse;
import in.devmedi.kiosk.module.ai.conversation.AiQuestionValidation;
import in.devmedi.kiosk.module.ai.conversation.AiQuestionValidationContext;
import in.devmedi.kiosk.module.ai.conversation.AiQuestionValidator;
import in.devmedi.kiosk.module.ai.conversation.AiRejectionReason;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AiQuestionValidatorTests {

    private final AiQuestionValidator validator = new AiQuestionValidator();

    private AiQuestionValidationContext context(String... allowedTopics) {
        return AiQuestionValidationContext.of(Set.of(allowedTopics), "HISTORY_OF_PRESENT_ILLNESS");
    }

    private AiQuestionValidationContext objectiveContext(Set<String> allowed,
                                                         String target,
                                                         String latestAnswer) {
        return AiQuestionValidationContext.of(allowed, Set.of(), List.of(),
                target, latestAnswer, "HISTORY_OF_PRESENT_ILLNESS");
    }

    @Test
    void acceptsNaturalQuestionEndingWithQuestionMark() {
        AiQuestionValidation result = validator.validate(
                AiQuestionResponse.of("On a scale of 0 to 10, how severe would you say it feels right now?",
                        "ONSET", false),
                context("ONSET"));

        assertThat(result.valid()).isTrue();
    }

    @Test
    void acceptsPromptStyleWordingWithoutQuestionMark() {
        AiQuestionValidation result = validator.validate(
                AiQuestionResponse.of("Please tell me a little more about the pain you mentioned.", null, null),
                context("PROVOCATION_PALLIATION"));

        assertThat(result.valid()).isTrue();
    }

    @Test
    void acceptsExplicitAllowedTopic() {
        AiQuestionValidation result = validator.validate(
                AiQuestionResponse.of("Does it stay constant or come and go?", "TIMING_DURATION", true),
                context("TIMING_DURATION"));

        assertThat(result.valid()).isTrue();
    }

    @Test
    void rejectsNullResponse() {
        AiQuestionValidation result = validator.validate(null, context("ONSET"));

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isEqualTo(AiRejectionReason.BLANK_QUESTION);
    }

    @Test
    void rejectsBlankQuestion() {
        AiQuestionValidation result = validator.validate(
                AiQuestionResponse.of("   ", null, null), context("ONSET"));

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isEqualTo(AiRejectionReason.BLANK_QUESTION);
    }

    @Test
    void rejectsQuestionThatIsTooShort() {
        AiQuestionValidation result = validator.validate(
                AiQuestionResponse.of("Hi", null, null), context("ONSET"));

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isEqualTo(AiRejectionReason.QUESTION_TOO_SHORT);
    }

    @Test
    void rejectsQuestionThatIsTooLong() {
        String tooLong = "?".repeat(301);
        AiQuestionValidation result = validator.validate(
                AiQuestionResponse.of(tooLong, null, null), context("ONSET"));

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isEqualTo(AiRejectionReason.QUESTION_TOO_LONG);
    }

    @Test
    void rejectsOutputThatIsNotAQuestion() {
        AiQuestionValidation result = validator.validate(
                AiQuestionResponse.of("Your answer is fine.", null, null), context("ONSET"));

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isEqualTo(AiRejectionReason.NOT_QUESTION_LIKE);
    }

    @Test
    void rejectsInventedMedicationDose() {
        AiQuestionValidation result = validator.validate(
                AiQuestionResponse.of("You should take 500 mg of a supplement for it.", null, null),
                context("ONSET"));

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isEqualTo(AiRejectionReason.FORBIDDEN_CLINICAL_CLAIM);
    }

    @Test
    void rejectsTreatmentRecommendation() {
        AiQuestionValidation result = validator.validate(
                AiQuestionResponse.of("I recommend you rest and start treatment.", null, null),
                context("ONSET"));

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isEqualTo(AiRejectionReason.FORBIDDEN_CLINICAL_CLAIM);
    }

    @Test
    void rejectsAssertedDiagnosis() {
        AiQuestionValidation result = validator.validate(
                AiQuestionResponse.of("Is the pain present because you have diabetes?", null, null),
                context("ONSET"));

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isEqualTo(AiRejectionReason.FORBIDDEN_CLINICAL_CLAIM);
    }

    @Test
    void rejectsInventedLabValueReference() {
        AiQuestionValidation result = validator.validate(
                AiQuestionResponse.of("What was your blood sugar reading last week?", null, null),
                context("ONSET"));

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isEqualTo(AiRejectionReason.FORBIDDEN_CLINICAL_CLAIM);
    }

    @Test
    void rejectsTopicOutsideCurrentSection() {
        AiQuestionValidation result = validator.validate(
                AiQuestionResponse.of("When did it start?", "VIKRITI", false),
                context("ONSET"));

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isEqualTo(AiRejectionReason.TOPIC_OUT_OF_SECTION);
    }

    @Test
    void acceptsQuestionWithNoTopicClaimed() {
        AiQuestionValidation result = validator.validate(
                AiQuestionResponse.of("When did this start for you?", null, null), context("ONSET"));

        assertThat(result.valid()).isTrue();
    }

    @Test
    void acceptsAdaptiveQuestionStayingInsideObjective() {
        AiQuestionValidation result = validator.validate(
                AiQuestionResponse.of(AiQuestionMode.QUESTION,
                        "You mentioned your stomach — when did the problem first start?", null, null),
                objectiveContext(Set.of("ONSET"), "When did it first start?", "My stomach hurts"));

        assertThat(result.valid()).isTrue();
    }

    @Test
    void acceptsClarificationTiedToLatestAnswer() {
        AiQuestionValidation result = validator.validate(
                AiQuestionResponse.of(AiQuestionMode.CLARIFICATION,
                        "You mentioned a dull ache — tell me more about that?", null, null),
                objectiveContext(Set.of("QUALITY"), "How would you describe the quality of the pain?",
                        "It's a dull ache at night"));

        assertThat(result.valid()).isTrue();
    }

    @Test
    void acceptsClarificationAnchoredByAllowedTopic() {
        AiQuestionValidation result = validator.validate(
                AiQuestionResponse.of(AiQuestionMode.CLARIFICATION,
                        "Tell me more about how it feels.", "QUALITY", null),
                objectiveContext(Set.of("QUALITY"), "How would you describe the quality of the pain?",
                        "It's tight"));

        assertThat(result.valid()).isTrue();
    }

    @Test
    void rejectsQuestionLeavingTheObjective() {
        AiQuestionValidation result = validator.validate(
                AiQuestionResponse.of(AiQuestionMode.QUESTION,
                        "Have you noticed any weight loss recently?", null, null),
                objectiveContext(Set.of("QUALITY"), "How would you describe the quality of the pain?",
                        "A dull ache"));

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isEqualTo(AiRejectionReason.OBJECTIVE_OUT_OF_BOUNDS);
    }

    @Test
    void rejectsMedicationDosageQuestionEvenInsideSection() {
        AiQuestionValidation result = validator.validate(
                AiQuestionResponse.of(AiQuestionMode.QUESTION,
                        "You should take 500 mg of the painkiller for it.", null, null),
                objectiveContext(Set.of("ONSET"), "When did it first start?", "A few days ago"));

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isEqualTo(AiRejectionReason.FORBIDDEN_CLINICAL_CLAIM);
    }

    @Test
    void rejectsDuplicateOfAnAlreadyAskedQuestion() {
        AiQuestionValidationContext full = AiQuestionValidationContext.of(
                Set.of("QUALITY"), Set.of(),
                List.of("When did it first start?"),
                "How would you describe the quality of the pain?",
                "A dull ache", "HISTORY_OF_PRESENT_ILLNESS");

        AiQuestionValidation result = validator.validate(
                AiQuestionResponse.of(AiQuestionMode.QUESTION,
                        "When exactly did it first start?", "QUALITY", null),
                full);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isEqualTo(AiRejectionReason.DUPLICATE_QUESTION);
    }

    @Test
    void rejectsTopicThatAlreadyWasCovered() {
        AiQuestionValidationContext full = AiQuestionValidationContext.of(
                Set.of("ONSET"), Set.of("ONSET"),
                List.of(), "How severe is the pain?", "Severe", "HISTORY_OF_PRESENT_ILLNESS");

        AiQuestionValidation result = validator.validate(
                AiQuestionResponse.of(AiQuestionMode.QUESTION,
                        "Anything else about the onset?", "ONSET", null),
                full);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isEqualTo(AiRejectionReason.TOPIC_ALREADY_COVERED);
    }

    @Test
    void acceptsCanonicalModeWithoutAnyQuestion() {
        AiQuestionValidation result = validator.validate(
                AiQuestionResponse.of(AiQuestionMode.CANONICAL, null, null, null),
                context("ONSET"));

        assertThat(result.valid()).isTrue();
    }

    @Test
    void rejectsMissingModeEvenWithPlausibleQuestion() {
        AiQuestionValidation result = validator.validate(
                new AiQuestionResponse(null, "When did it start?", null, null),
                context("ONSET"));

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isEqualTo(AiRejectionReason.MODE_MISSING_OR_INVALID);
    }
}