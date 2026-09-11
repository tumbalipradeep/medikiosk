package in.devmedi.kiosk.module.clinical.ai;

import in.devmedi.kiosk.module.ai.conversation.AiQuestionMode;
import in.devmedi.kiosk.module.ai.conversation.AiQuestionParseException;
import in.devmedi.kiosk.module.ai.conversation.AiQuestionRequest;
import in.devmedi.kiosk.module.ai.conversation.AiQuestionResponse;
import in.devmedi.kiosk.module.ai.conversation.AiQuestionValidation;
import in.devmedi.kiosk.module.ai.conversation.AiQuestionValidationContext;
import in.devmedi.kiosk.module.ai.conversation.AiQuestionValidator;
import in.devmedi.kiosk.module.ai.conversation.ConversationTurn;
import in.devmedi.kiosk.module.ai.conversation.QuestionGenerationSpec;
import in.devmedi.kiosk.module.ai.provider.AiFailoverService;
import in.devmedi.kiosk.module.ai.provider.AiProviderException;
import in.devmedi.kiosk.module.ai.provider.AiProviderFailure;
import in.devmedi.kiosk.module.ai.conversation.ClinicalAiRequest;
import in.devmedi.kiosk.module.ai.conversation.ClinicalAiResponse;
import in.devmedi.kiosk.module.clinical.dialogue.ClinicalAnswer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Orchestrates adaptive AI wording for the deterministic clinical intake.
 *
 * <p>The deterministic clinical backbone stays authoritative: it decides the
 * section, objective, progression, completion, mandatory questions, and
 * red-flag evaluation. This service only fills the <em>wording slot</em> of the
 * next deterministic question, letting a validated AI provider rephrase it
 * naturally ({@link AiQuestionMode#QUESTION}) or ask a short clarification of the
 * patient's latest answer ({@link AiQuestionMode#CLARIFICATION}) — both strictly
 * inside the current objective. Whenever the provider fails, returns malformed
 * or unsafe output, leaves the objective, or repeats an already-asked question,
 * the canonical deterministic question text is used unchanged.</p>
 *
 * <p>Provider handling: providers are attempted strictly in the configured
 * failover order, one at a time. Without any enabled provider the service
 * returns the canonical deterministic question immediately (zero network
 * traffic). Under no circumstance can an AI failure break the conversation.</p>
 */
@Service
public class AiConversationService {

    private static final Logger log = LoggerFactory.getLogger(AiConversationService.class);

    /** Compact conversation history retained for AI context (excludes the current turn). */
    private static final int MAX_CONTEXT_TURNS = 6;

    private final AiFailoverService failoverService;
    private final AiQuestionValidator questionValidator;

    public AiConversationService(AiFailoverService failoverService, AiQuestionValidator questionValidator) {
        this.failoverService = failoverService;
        this.questionValidator = questionValidator;
    }

    /**
     * @return true when at least one AI provider is configured and enabled.
     */
    public boolean aiAvailable() {
        return failoverService.hasEnabledProviders();
    }

    /**
     * Produces the wording for the next deterministic question.
     *
     * @param section            current deterministic clinical section name
     * @param questionTopic      facet/parameter the deterministic next question collects (the allowed objective)
     * @param targetQuestionText canonical deterministic next-question text
     * @param latestAnswer       the patient's latest verbatim answer
     * @param recentAnswers      previously collected answers (the current one may be included as last)
     * @param language           ISO language code for the patient-facing question
     * @return either validated AI wording or the canonical deterministic question text
     */
    public NextQuestionWording nextQuestionWording(String section,
                                                   String questionTopic,
                                                   String targetQuestionText,
                                                   String latestAnswer,
                                                   List<ClinicalAnswer> recentAnswers,
                                                   String language) {
        long start = System.nanoTime();
        try {
            if (!failoverService.hasEnabledProviders()) {
                return fallback(targetQuestionText, AiProviderFailure.NO_PROVIDERS_AVAILABLE, null, start);
            }

            ConversationContext context = conversationContext(recentAnswers);
            List<ConversationTurn> priorTurns = toPriorTurns(recentAnswers, latestAnswer);
            Set<String> allowedTopics = Set.of(questionTopic);
            AiQuestionRequest aiRequest = AiQuestionRequest.builder()
                    .language(language)
                    .section(section)
                    .questionTopic(questionTopic)
                    .targetQuestionText(targetQuestionText)
                    .currentQuestionText(context.currentQuestionText)
                    .currentQuestionTopic(context.currentQuestionTopic)
                    .patientAnswer(latestAnswer)
                    .previousTurns(priorTurns)
                    .allowedFollowUpTopics(List.copyOf(allowedTopics))
                    .alreadyAskedQuestions(context.alreadyAskedQuestions)
                    .askedTopics(context.askedTopics)
                    .build();

            ClinicalAiResponse response = failoverService.complete(toProviderRequest(aiRequest));
            final AiQuestionResponse parsed;
            try {
                parsed = AiQuestionResponse.fromJson(response.getContent());
            } catch (AiQuestionParseException ex) {
                return fallbackWithCategory(targetQuestionText, AiProviderFailure.MALFORMED_RESPONSE,
                        ex.getReason().name(), start);
            }

            if (parsed.mode() == AiQuestionMode.CANONICAL) {
                log.info("AI conversation: source=MODEL_REQUESTED_CANONICAL provider={} model={} durationMs={}",
                        response.getProvider(), response.getModel(), elapsedMs(start));
                return NextQuestionWording.deterministicFallback(targetQuestionText, null,
                        "MODEL_REQUESTED_CANONICAL", elapsedMs(start));
            }

            AiQuestionValidation validation = questionValidator.validate(parsed,
                    AiQuestionValidationContext.of(
                            allowedTopics,
                            Set.copyOf(context.askedTopics),
                            context.alreadyAskedQuestions,
                            targetQuestionText,
                            latestAnswer,
                            section));
            if (!validation.valid()) {
                return fallbackWithCategory(targetQuestionText, AiProviderFailure.VALIDATION_REJECTED,
                        validation.reason() == null ? null : validation.reason().name(), start);
            }

            return aiGenerated(trimQuestion(parsed.question()).orElse(targetQuestionText),
                    response.getProvider(), response.getModel(), start);
        } catch (AiProviderException ex) {
            // Per-provider failure categories are logged by the failover service.
            return fallback(targetQuestionText, AiProviderFailure.classify(ex), null, start);
        } catch (RuntimeException ex) {
            log.error("AI question generation aborted unexpectedly; using deterministic fallback", ex);
            return fallback(targetQuestionText, AiProviderFailure.UNSPECIFIED_FAILURE, null, start);
        }
    }

    private ClinicalAiRequest toProviderRequest(AiQuestionRequest aiRequest) {
        return ClinicalAiRequest.builder()
                .language(aiRequest.language())
                .currentQuestion(aiRequest.targetQuestionText())
                .patientAnswer(aiRequest.patientAnswer())
                .conversation(aiRequest.previousTurns())
                .requestedResponse(QuestionGenerationSpec.buildPrompt(aiRequest))
                .build();
    }

    /**
     * Distilled state derived from the collected answers: the canonical question
     * the patient just answered (their latest answer's referent) plus the full,
     * deduplicated lists of asked questions and covered topics.
     */
    private ConversationContext conversationContext(List<ClinicalAnswer> recentAnswers) {
        if (recentAnswers == null || recentAnswers.isEmpty()) {
            return new ConversationContext(null, null, List.of(), List.of());
        }
        ClinicalAnswer current = recentAnswers.get(recentAnswers.size() - 1);
        LinkedHashSet<String> askedQuestions = new LinkedHashSet<>();
        LinkedHashSet<String> askedTopics = new LinkedHashSet<>();
        for (ClinicalAnswer answer : recentAnswers) {
            if (answer.questionText() != null && !answer.questionText().isBlank()) {
                askedQuestions.add(answer.questionText());
            }
            if (answer.questionType() != null && !answer.questionType().isBlank()) {
                askedTopics.add(answer.questionType());
            }
        }
        return new ConversationContext(current.questionText(), current.questionType(),
                List.copyOf(askedQuestions), List.copyOf(askedTopics));
    }

    private List<ConversationTurn> toPriorTurns(List<ClinicalAnswer> recentAnswers, String latestAnswer) {
        if (recentAnswers == null || recentAnswers.isEmpty()) {
            return List.of();
        }
        List<ClinicalAnswer> prior = new ArrayList<>(recentAnswers);
        if (latestAnswer != null && !prior.isEmpty()) {
            ClinicalAnswer last = prior.get(prior.size() - 1);
            if (last.answer() != null && last.answer().equals(latestAnswer)) {
                prior.remove(prior.size() - 1);
            }
        }
        int from = Math.max(0, prior.size() - MAX_CONTEXT_TURNS);
        List<ConversationTurn> turns = new ArrayList<>();
        for (int i = from; i < prior.size(); i++) {
            ClinicalAnswer answer = prior.get(i);
            turns.add(new ConversationTurn(answer.questionText(), answer.answer()));
        }
        return List.copyOf(turns);
    }

    private java.util.Optional<String> trimQuestion(String question) {
        String trimmed = question == null ? "" : question.trim();
        return trimmed.isBlank() ? java.util.Optional.empty() : java.util.Optional.of(trimmed);
    }

    private NextQuestionWording aiGenerated(String questionText, String provider, String model, long start) {
        long durationMs = elapsedMs(start);
        log.info("AI conversation: source=AI_GENERATED provider={} model={} durationMs={}", provider, model, durationMs);
        return NextQuestionWording.aiGenerated(questionText, provider, model, durationMs);
    }

    private NextQuestionWording fallback(String canonical, AiProviderFailure failure, String rejectionReason, long start) {
        return fallbackWithCategory(canonical, failure, rejectionReason, start);
    }

    private NextQuestionWording fallbackWithCategory(String canonical, AiProviderFailure failure,
                                                     String rejectionReason, long start) {
        long durationMs = elapsedMs(start);
        log.info("AI conversation: source=DETERMINISTIC_FALLBACK failure={} rejectionReason={} durationMs={}",
                failure, rejectionReason, durationMs);
        return NextQuestionWording.deterministicFallback(canonical, failure, rejectionReason, durationMs);
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private record ConversationContext(String currentQuestionText,
                                       String currentQuestionTopic,
                                       List<String> alreadyAskedQuestions,
                                       List<String> askedTopics) {
    }
}