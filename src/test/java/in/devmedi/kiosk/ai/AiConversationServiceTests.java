package in.devmedi.kiosk.ai;

import in.devmedi.kiosk.module.ai.conversation.AiQuestionValidator;
import in.devmedi.kiosk.module.ai.conversation.ClinicalAiRequest;
import in.devmedi.kiosk.module.ai.conversation.ClinicalAiResponse;
import in.devmedi.kiosk.module.ai.conversation.ConversationTurn;
import in.devmedi.kiosk.module.ai.conversation.QuestionGenerationSpec;
import in.devmedi.kiosk.module.ai.provider.AiFailoverService;
import in.devmedi.kiosk.module.ai.provider.AiProviderException;
import in.devmedi.kiosk.module.ai.provider.AiProviderFailure;
import in.devmedi.kiosk.module.clinical.ai.AiConversationService;
import in.devmedi.kiosk.module.clinical.ai.NextQuestionSource;
import in.devmedi.kiosk.module.clinical.ai.NextQuestionWording;
import in.devmedi.kiosk.module.clinical.dialogue.ClinicalAnswer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiConversationServiceTests {

    private static final String SECTION = "HISTORY_OF_PRESENT_ILLNESS";
    private static final String TOPIC = "ONSET";
    private static final String CANONICAL = "When did it first start?";

    @Mock
    private AiFailoverService failoverService;

    private AiConversationService service;

    @BeforeEach
    void setUp() {
        service = new AiConversationService(failoverService, new AiQuestionValidator());
    }

    private NextQuestionWording generate(String latestAnswer, List<ClinicalAnswer> recent) {
        return service.nextQuestionWording(SECTION, TOPIC, CANONICAL, latestAnswer, recent, "en");
    }

    private ClinicalAiResponse responseFrom(String provider, String jsonContent) {
        return ClinicalAiResponse.of(provider, "model-" + provider, jsonContent, "en");
    }

    @Test
    void withoutEnabledProvidersReturnsDeterministicImmediately() {
        when(failoverService.hasEnabledProviders()).thenReturn(false);

        NextQuestionWording wording = generate("A few days ago", List.of());

        assertThat(wording.source()).isEqualTo(NextQuestionSource.DETERMINISTIC_FALLBACK);
        assertThat(wording.text()).isEqualTo(CANONICAL);
        assertThat(wording.failure()).isEqualTo(AiProviderFailure.NO_PROVIDERS_AVAILABLE);
        verify(failoverService, never()).complete(any());
    }

    @Test
    void validatedAiWordingIsUsed() {
        when(failoverService.hasEnabledProviders()).thenReturn(true);
        when(failoverService.complete(any())).thenReturn(responseFrom("groq",
                "{\"mode\":\"QUESTION\",\"question\":\"When did you first notice this problem start?\",\"topic\":\"ONSET\",\"continue\":true}"));

        NextQuestionWording wording = generate("A few days ago", List.of());

        assertThat(wording.source()).isEqualTo(NextQuestionSource.AI_GENERATED);
        assertThat(wording.provider()).isEqualTo("groq");
        assertThat(wording.text()).isEqualTo("When did you first notice this problem start?");
    }

    @Test
    void responseFromSecondaryProviderAfterFailoverIsUsed() {
        when(failoverService.hasEnabledProviders()).thenReturn(true);
        when(failoverService.complete(any())).thenReturn(responseFrom("gemini",
                "{\"mode\":\"QUESTION\",\"question\":\"Could you tell me when this began?\"}"));

        NextQuestionWording wording = generate("A few days ago", List.of());

        assertThat(wording.source()).isEqualTo(NextQuestionSource.AI_GENERATED);
        assertThat(wording.provider()).isEqualTo("gemini");
    }

    @Test
    void allProvidersFailingFallsBackToDeterministic() {
        when(failoverService.hasEnabledProviders()).thenReturn(true);
        when(failoverService.complete(any())).thenThrow(
                new AiProviderException("all providers failed: groq: boom | gemini: timeout | openrouter: 503"));

        NextQuestionWording wording = generate("A few days ago", List.of());

        assertThat(wording.source()).isEqualTo(NextQuestionSource.DETERMINISTIC_FALLBACK);
        assertThat(wording.text()).isEqualTo(CANONICAL);
        assertThat(wording.failure()).isEqualTo(AiProviderFailure.classify(
                new AiProviderException("boom")));
    }

    @Test
    void malformedJsonResponseFallsBackToDeterministic() {
        when(failoverService.hasEnabledProviders()).thenReturn(true);
        when(failoverService.complete(any())).thenReturn(responseFrom("groq", "I am not JSON at all"));

        NextQuestionWording wording = generate("A few days ago", List.of());

        assertThat(wording.source()).isEqualTo(NextQuestionSource.DETERMINISTIC_FALLBACK);
        assertThat(wording.text()).isEqualTo(CANONICAL);
        assertThat(wording.failure()).isEqualTo(AiProviderFailure.MALFORMED_RESPONSE);
        assertThat(wording.rejectionReason()).isEqualTo("INVALID_JSON");
    }

    @Test
    void blankCompletionFallsBackToDeterministic() {
        when(failoverService.hasEnabledProviders()).thenReturn(true);
        when(failoverService.complete(any())).thenReturn(responseFrom("groq", ""));

        NextQuestionWording wording = generate("A few days ago", List.of());

        assertThat(wording.source()).isEqualTo(NextQuestionSource.DETERMINISTIC_FALLBACK);
        assertThat(wording.failure()).isEqualTo(AiProviderFailure.MALFORMED_RESPONSE);
        assertThat(wording.text()).isEqualTo(CANONICAL);
    }

    @Test
    void unsafeClinicalClaimIsRejectedAndFallsBack() {
        when(failoverService.hasEnabledProviders()).thenReturn(true);
        when(failoverService.complete(any())).thenReturn(responseFrom("groq",
                "{\"mode\":\"QUESTION\",\"question\":\"You should take 500 mg of a supplement for it.\"}"));

        NextQuestionWording wording = generate("A few days ago", List.of());

        assertThat(wording.source()).isEqualTo(NextQuestionSource.DETERMINISTIC_FALLBACK);
        assertThat(wording.text()).isEqualTo(CANONICAL);
        assertThat(wording.failure()).isEqualTo(AiProviderFailure.VALIDATION_REJECTED);
        assertThat(wording.rejectionReason()).isEqualTo("FORBIDDEN_CLINICAL_CLAIM");
    }

    @Test
    void outOfSectionTopicIsRejectedAndFallsBack() {
        when(failoverService.hasEnabledProviders()).thenReturn(true);
        when(failoverService.complete(any())).thenReturn(responseFrom("groq",
                "{\"mode\":\"QUESTION\",\"question\":\"When did it start?\",\"topic\":\"VIKRITI\"}"));

        NextQuestionWording wording = generate("A few days ago", List.of());

        assertThat(wording.source()).isEqualTo(NextQuestionSource.DETERMINISTIC_FALLBACK);
        assertThat(wording.failure()).isEqualTo(AiProviderFailure.VALIDATION_REJECTED);
        assertThat(wording.rejectionReason()).isEqualTo("TOPIC_OUT_OF_SECTION");
    }

    @Test
    void unexpectedRuntimeExceptionNeverBreaksConversation() {
        when(failoverService.hasEnabledProviders()).thenReturn(true);
        when(failoverService.complete(any())).thenThrow(new IllegalStateException("explosion inside provider"));

        NextQuestionWording wording = generate("A few days ago", List.of());

        assertThat(wording.source()).isEqualTo(NextQuestionSource.DETERMINISTIC_FALLBACK);
        assertThat(wording.text()).isEqualTo(CANONICAL);
        assertThat(wording.failure()).isEqualTo(AiProviderFailure.UNSPECIFIED_FAILURE);
    }

    @Test
    void aiAvailableReflectsFailover() {
        when(failoverService.hasEnabledProviders()).thenReturn(true);
        assertThat(service.aiAvailable()).isTrue();
    }

    @Test
    void modelRequestingCanonicalUsesDeterministicText() {
        when(failoverService.hasEnabledProviders()).thenReturn(true);
        when(failoverService.complete(any())).thenReturn(responseFrom("groq", "{\"mode\":\"CANONICAL\"}"));

        NextQuestionWording wording = generate("A few days ago", List.of());

        assertThat(wording.source()).isEqualTo(NextQuestionSource.DETERMINISTIC_FALLBACK);
        assertThat(wording.text()).isEqualTo(CANONICAL);
        assertThat(wording.rejectionReason()).isEqualTo("MODEL_REQUESTED_CANONICAL");
    }

    @Test
    void adaptiveQuestionInsideObjectiveIsUsed() {
        when(failoverService.hasEnabledProviders()).thenReturn(true);
        when(failoverService.complete(any())).thenReturn(responseFrom("groq",
                "{\"mode\":\"QUESTION\",\"question\":\"You mentioned your stomach - when did the problem first start?\",\"topic\":\"ONSET\"}"));

        List<ClinicalAnswer> recent = List.of(new ClinicalAnswer(
                "chief_complaint_symptom", "CHIEF_COMPLAINT", "SYMPTOM_PROBLEM",
                "What is the main problem or symptom that brought you here today?", "My stomach hurts"));

        NextQuestionWording wording = generate("My stomach hurts", recent);

        assertThat(wording.source()).isEqualTo(NextQuestionSource.AI_GENERATED);
        assertThat(wording.text()).isEqualTo("You mentioned your stomach - when did the problem first start?");
    }

    @Test
    void clarificationWithinObjectiveIsUsed() {
        when(failoverService.hasEnabledProviders()).thenReturn(true);
        when(failoverService.complete(any())).thenReturn(responseFrom("gemini",
                "{\"mode\":\"CLARIFICATION\",\"question\":\"You said a dull ache at night - tell me more about that?\"}"));

        List<ClinicalAnswer> recent = List.of(new ClinicalAnswer(
                "chief_complaint_symptom", "CHIEF_COMPLAINT", "SYMPTOM_PROBLEM",
                "What is the main problem or symptom that brought you here today?", "It's a dull ache at night"));

        NextQuestionWording wording = service.nextQuestionWording(SECTION, "QUALITY",
                "How would you describe the quality of the pain?", "It's a dull ache at night", recent, "en");

        assertThat(wording.source()).isEqualTo(NextQuestionSource.AI_GENERATED);
        assertThat(wording.text()).isEqualTo("You said a dull ache at night - tell me more about that?");
    }

    @Test
    void duplicateRequestionFallsBackToCanonical() {
        when(failoverService.hasEnabledProviders()).thenReturn(true);
        when(failoverService.complete(any())).thenReturn(responseFrom("groq",
                "{\"mode\":\"QUESTION\",\"question\":\"When exactly did it first start?\",\"topic\":\"QUALITY\"}"));

        List<ClinicalAnswer> recent = List.of(
                new ClinicalAnswer("chief_complaint_symptom", "CHIEF_COMPLAINT", "SYMPTOM_PROBLEM",
                        "What is the main problem or symptom that brought you here today?", "A cough"),
                new ClinicalAnswer("hpi_onset", SECTION, TOPIC, CANONICAL, "A few days ago"));

        NextQuestionWording wording = service.nextQuestionWording(SECTION, "QUALITY",
                "How would you describe the quality of the pain?", "A few days ago", recent, "en");

        assertThat(wording.source()).isEqualTo(NextQuestionSource.DETERMINISTIC_FALLBACK);
        assertThat(wording.text()).isEqualTo("How would you describe the quality of the pain?");
        assertThat(wording.failure()).isEqualTo(AiProviderFailure.VALIDATION_REJECTED);
        assertThat(wording.rejectionReason()).isEqualTo("DUPLICATE_QUESTION");
    }

    @Test
    void questionOutsideObjectiveFallsBackToCanonical() {
        when(failoverService.hasEnabledProviders()).thenReturn(true);
        when(failoverService.complete(any())).thenReturn(responseFrom("groq",
                "{\"mode\":\"QUESTION\",\"question\":\"Have you noticed any weight loss?\"}"));

        List<ClinicalAnswer> recent = List.of(new ClinicalAnswer(
                "chief_complaint_symptom", "CHIEF_COMPLAINT", "SYMPTOM_PROBLEM",
                "What is the main problem or symptom that brought you here today?", "A dull ache"));

        NextQuestionWording wording = service.nextQuestionWording(SECTION, "QUALITY",
                "How would you describe the quality of the pain?", "A dull ache", recent, "en");

        assertThat(wording.source()).isEqualTo(NextQuestionSource.DETERMINISTIC_FALLBACK);
        assertThat(wording.text()).isEqualTo("How would you describe the quality of the pain?");
        assertThat(wording.rejectionReason()).isEqualTo("OBJECTIVE_OUT_OF_BOUNDS");
    }

    @Test
    void requestCarriesOnlyMinimalContext() {
        when(failoverService.hasEnabledProviders()).thenReturn(true);
        when(failoverService.complete(any())).thenReturn(responseFrom("groq",
                "{\"mode\":\"QUESTION\",\"question\":\"When did it start?\"}"));

        List<ClinicalAnswer> recent = List.of(
                new ClinicalAnswer("chief_complaint_symptom", "CHIEF_COMPLAINT", "SYMPTOM_PROBLEM",
                        "What is the main problem or symptom that brought you here today?", "Cough"),
                new ClinicalAnswer("hpi_onset", SECTION, TOPIC, CANONICAL, "A few days ago"));

        generate("A few days ago", recent);

        ArgumentCaptor<ClinicalAiRequest> captor = ArgumentCaptor.forClass(ClinicalAiRequest.class);
        verify(failoverService).complete(captor.capture());
        ClinicalAiRequest request = captor.getValue();

        assertThat(request.getPatientAnswer()).isEqualTo("A few days ago");
        assertThat(request.getCurrentQuestion()).isEqualTo(CANONICAL);
        assertThat(request.getConversation()).hasSize(1);
        assertThat(request.getConversation().get(0)).isEqualTo(
                new ConversationTurn("What is the main problem or symptom that brought you here today?", "Cough"));
        assertThat(request.getRequestedResponse()).contains(QuestionGenerationSpec.SAFETY);
        assertThat(request.getRequestedResponse()).contains("ONSET");
        assertThat(request.getRequestedResponse()).contains("A few days ago");
        assertThat(request.getRequestedResponse()).contains("SYMPTOM_PROBLEM");
    }
}