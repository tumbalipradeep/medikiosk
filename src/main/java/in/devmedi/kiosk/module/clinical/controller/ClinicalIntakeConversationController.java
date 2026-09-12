package in.devmedi.kiosk.module.clinical.controller;

import in.devmedi.kiosk.module.auth.security.ApplicationUserDetails;
import in.devmedi.kiosk.module.clinical.ai.AiConversationService;
import in.devmedi.kiosk.module.clinical.ai.NextQuestionSource;
import in.devmedi.kiosk.module.clinical.ai.NextQuestionWording;
import in.devmedi.kiosk.module.clinical.ayush.AharaViharaQuestion;
import in.devmedi.kiosk.module.clinical.ayush.DashavidhaQuestion;
import in.devmedi.kiosk.module.clinical.ayush.AharaViharaQuestionPlanner;
import in.devmedi.kiosk.module.clinical.ayush.DashavidhaQuestionPlanner;
import in.devmedi.kiosk.module.clinical.dialogue.ClinicalAnswer;
import in.devmedi.kiosk.module.clinical.dialogue.ClinicalConversationAnswerRequest;
import in.devmedi.kiosk.module.clinical.dialogue.ClinicalConversationResult;
import in.devmedi.kiosk.module.clinical.dialogue.ClinicalIntakeResponse;
import in.devmedi.kiosk.module.clinical.dialogue.ClinicalQuestion;
import in.devmedi.kiosk.module.clinical.dialogue.DialogueState;
import in.devmedi.kiosk.module.clinical.dialogue.IntakeQuestion;
import in.devmedi.kiosk.module.clinical.dialogue.QuestionPlanner;
import in.devmedi.kiosk.module.clinical.dialogue.QuestionSource;
import in.devmedi.kiosk.module.clinical.dialogue.AnswerSource;
import in.devmedi.kiosk.module.clinical.i18n.QuestionLocalizationService;
import in.devmedi.kiosk.module.clinical.redflag.RedFlag;
import in.devmedi.kiosk.module.clinical.redflag.RedFlagEvaluator;
import in.devmedi.kiosk.module.clinical.redflag.RedFlagSeverity;
import in.devmedi.kiosk.module.patientsession.service.PatientSessionService;
import in.devmedi.kiosk.module.physician.service.CompletedCase;
import in.devmedi.kiosk.module.physician.service.CompletedCasePersistenceService;
import in.devmedi.kiosk.module.physician.service.CompletedCaseReviewStore;
import in.devmedi.kiosk.module.voice.language.LanguageService;
import in.devmedi.kiosk.module.voice.language.SupportedLanguage;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Backend step endpoint for the patient clinical intake conversation.
 *
 * <p>Stateless and deterministic: the client sends the id of the question it just
 * answered, the owning planner returns exactly the next question, and the
 * red-flag evaluator inspects the answer for urgent warning signs. The combined
 * sequence runs the unchanged QuestionPlanner (SOCRATES/HPI) first, hands over
 * to the DashavidhaQuestionPlanner after the final HPI answer, then to the
 * AharaViharaQuestionPlanner after the tenth Dashavidha parameter, and finally
 * completes after the eighth Ahara-Vihara parameter. Every submitted answer is
 * also captured, in order, into an in-memory {@link ClinicalConversationResult}
 * kept in the HTTP session. When the intake completes, that result is handed to
 * the physician review store and persisted as a completed case, and the
 * patient's session is marked completed so a genuinely finished patient can
 * start again cleanly.
 *
 * <p>Presentation language (M5.1): the patient's selected language is resolved
 * per request. For every non-English language the canonical English question is
 * replaced by a curated offline translation, recorded honestly as
 * {@link QuestionSource#TRANSLATED} with the language actually used. AI
 * conversational rewording is only ever offered for English; no AI multilingual
 * capability is claimed.</p>
 *
 * <p>AI assistance (adaptive): when an AI provider is configured, a validated
 * provider may phrase the next deterministic question conversationally or ask a
 * short in-objective clarification of the latest answer. The question id,
 * section, type, objective, progression, completion, and red-flag evaluation
 * always stay deterministic. The wording actually shown and its source (AI or
 * deterministic) are recorded alongside the canonical question and the patient's
 * verbatim answer, so the clinical record preserves the exact truth. Without
 * credentials, or on any provider or validation failure, the canonical
 * deterministic question is used unchanged.</p>
 *
 * <p>Duplicate-protection: a case is persisted exactly once per HTTP session.
 * The final answer registers and saves the completed case, remembers its
 * identity in the session, and marks the patient session COMPLETED; a repeated
 * final answer (double-click, refresh, back) returns the same completed response
 * with the same case id and re-saves nothing.</p>
 */
@RestController
@RequestMapping("/patient/intake/conversation")
public class ClinicalIntakeConversationController {

    public static final String RESULT_ATTRIBUTE = "clinicalConversationResult";
    public static final String DISPLAYED_ATTRIBUTE = "clinicalConversationDisplayed";

    /** HTTP session attribute holding the case id once a case has been persisted. */
    public static final String COMPLETED_CASE_ATTRIBUTE = "clinicalConversationCompletedCaseId";

    private final QuestionPlanner questionPlanner;
    private final DashavidhaQuestionPlanner dashavidhaQuestionPlanner;
    private final AharaViharaQuestionPlanner aharaViharaQuestionPlanner;
    private final RedFlagEvaluator redFlagEvaluator;
    private final CompletedCaseReviewStore reviewStore;
    private final CompletedCasePersistenceService casePersistence;
    private final AiConversationService aiConversationService;
    private final LanguageService languageService;
    private final QuestionLocalizationService questionLocalization;
    private final PatientSessionService patientSessionService;

    private final Set<String> clinicalQuestionIds;
    private final Set<String> dashavidhaQuestionIds;
    private final Set<String> aharaViharaQuestionIds;
    private final int totalQuestions;

    public ClinicalIntakeConversationController(QuestionPlanner questionPlanner,
                                                DashavidhaQuestionPlanner dashavidhaQuestionPlanner,
                                                AharaViharaQuestionPlanner aharaViharaQuestionPlanner,
                                                RedFlagEvaluator redFlagEvaluator,
                                                CompletedCaseReviewStore reviewStore,
                                                CompletedCasePersistenceService casePersistence,
                                                AiConversationService aiConversationService,
                                                LanguageService languageService,
                                                QuestionLocalizationService questionLocalization,
                                                PatientSessionService patientSessionService) {
        this.questionPlanner = questionPlanner;
        this.dashavidhaQuestionPlanner = dashavidhaQuestionPlanner;
        this.aharaViharaQuestionPlanner = aharaViharaQuestionPlanner;
        this.redFlagEvaluator = redFlagEvaluator;
        this.reviewStore = reviewStore;
        this.casePersistence = casePersistence;
        this.aiConversationService = aiConversationService;
        this.languageService = languageService;
        this.questionLocalization = questionLocalization;
        this.patientSessionService = patientSessionService;
        this.clinicalQuestionIds = questionPlanner.questions().stream()
                .map(ClinicalQuestion::id)
                .collect(Collectors.toUnmodifiableSet());
        this.dashavidhaQuestionIds = dashavidhaQuestionPlanner.questions().stream()
                .map(DashavidhaQuestion::id)
                .collect(Collectors.toUnmodifiableSet());
        this.aharaViharaQuestionIds = aharaViharaQuestionPlanner.questions().stream()
                .map(AharaViharaQuestion::id)
                .collect(Collectors.toUnmodifiableSet());
        this.totalQuestions = clinicalQuestionIds.size() + dashavidhaQuestionIds.size() + aharaViharaQuestionIds.size();
    }

    @PostMapping("/start")
    @ResponseStatus(HttpStatus.OK)
    public ClinicalIntakeResponse start(HttpSession session) {
        String completedCaseId = (String) session.getAttribute(COMPLETED_CASE_ATTRIBUTE);
        if (completedCaseId != null) {
            return completedResponse(completedCaseId);
        }
        ClinicalConversationResult result = resultFor(session);
        if (result.isEmpty()) {
            session.setAttribute(DISPLAYED_ATTRIBUTE, new LinkedHashMap<String, DisplayedQuestion>());
            IntakeQuestion first = localized(
                    IntakeQuestion.fromClinical(questionPlanner.firstQuestion()), resolvedLanguage(session));
            return new ClinicalIntakeResponse(first, DialogueState.IN_PROGRESS, false,
                    List.of(), RedFlagSeverity.NONE, null, 0, totalQuestions);
        }
        IntakeQuestion next = nextUnanswered(result);
        if (next == null) {
            List<RedFlag> flags = flagsFor(result);
            return completeReady(session, result, null, flags, redFlagEvaluator.overallSeverity(flags));
        }
        restockDisplayed(session, next, resolvedLanguage(session));
        return new ClinicalIntakeResponse(next, DialogueState.IN_PROGRESS, false,
                List.of(), RedFlagSeverity.NONE, null, result.size(), totalQuestions);
    }

    @PostMapping("/restart")
    @ResponseStatus(HttpStatus.OK)
    public ClinicalIntakeResponse restart(HttpSession session) {
        session.removeAttribute(COMPLETED_CASE_ATTRIBUTE);
        session.setAttribute(RESULT_ATTRIBUTE, new ClinicalConversationResult());
        session.setAttribute(DISPLAYED_ATTRIBUTE, new LinkedHashMap<String, DisplayedQuestion>());
        IntakeQuestion first = localized(
                IntakeQuestion.fromClinical(questionPlanner.firstQuestion()), resolvedLanguage(session));
        return new ClinicalIntakeResponse(first, DialogueState.IN_PROGRESS, false,
                List.of(), RedFlagSeverity.NONE, null, 0, totalQuestions);
    }

    @PostMapping("/answer")
    @ResponseStatus(HttpStatus.OK)
    public ClinicalIntakeResponse answer(@RequestBody ClinicalConversationAnswerRequest request,
                                         HttpSession session,
                                         @AuthenticationPrincipal ApplicationUserDetails principal) {
        if (request == null || request.questionId() == null || request.questionId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "questionId is required");
        }
        ClinicalConversationResult result = resultFor(session);
        Map<String, DisplayedQuestion> displayed = displayedFor(session);
        SupportedLanguage language = resolvedLanguage(session);
        AnswerSource answerSource = AnswerSource.normalize(request.answerSource());

        String questionId = request.questionId();
        if (clinicalQuestionIds.contains(questionId)) {
            return handleClinicalAnswer(questionId, request.answer(), answerSource, language, result, displayed);
        }
        if (dashavidhaQuestionIds.contains(questionId)) {
            return handleDashavidhaAnswer(questionId, request.answer(), answerSource, language, result, displayed);
        }
        if (aharaViharaQuestionIds.contains(questionId)) {
            return handleAharaViharaAnswer(questionId, request.answer(), answerSource, language, result, displayed,
                    session, principal);
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown question id: " + questionId);
    }

    /**
     * The patient language selected for the current intake session, resolving
     * deterministically to English when no selection has been made. Language is
     * presentation-layer state and never influences clinical progression.
     */
    private SupportedLanguage resolvedLanguage(HttpSession session) {
        String selected = (String) session.getAttribute(PatientIntakeLanguageController.LANGUAGE_ATTRIBUTE);
        return languageService.resolve(selected);
    }

    private ClinicalConversationResult resultFor(HttpSession session) {
        ClinicalConversationResult result = (ClinicalConversationResult) session.getAttribute(RESULT_ATTRIBUTE);
        if (result == null) {
            result = new ClinicalConversationResult();
            session.setAttribute(RESULT_ATTRIBUTE, result);
        }
        return result;
    }

    private Map<String, DisplayedQuestion> displayedFor(HttpSession session) {
        Map<String, DisplayedQuestion> displayed =
                (Map<String, DisplayedQuestion>) session.getAttribute(DISPLAYED_ATTRIBUTE);
        if (displayed == null) {
            displayed = new LinkedHashMap<>();
            session.setAttribute(DISPLAYED_ATTRIBUTE, displayed);
        }
        return displayed;
    }

    /**
     * Replaces the canonical English question with its curated translation when
     * the presentation language is not English. Falls back to the canonical text
     * (unchanged) for English or any question without a translation.
     */
    private IntakeQuestion localized(IntakeQuestion canonical, SupportedLanguage language) {
        String translated = questionLocalization.translation(canonical.id(), language);
        if (translated == null) {
            return canonical;
        }
        return new IntakeQuestion(canonical.id(), canonical.section(), canonical.type(),
                translated, canonical.required(), canonical.order());
    }

    /**
     * Advances within the unchanged SOCRATES/HPI sequence. After the final HPI
     * question the conversation transitions to the first Dashavidha question
     * rather than completing.
     */
    private ClinicalIntakeResponse handleClinicalAnswer(String questionId, String answer,
                                                       AnswerSource answerSource,
                                                       SupportedLanguage language,
                                                       ClinicalConversationResult result,
                                                       Map<String, DisplayedQuestion> displayed) {
        ClinicalQuestion answered = questionPlanner.question(questionId);
        recordAnswered(IntakeQuestion.fromClinical(answered), answer, answerSource, language, displayed, result);
        List<RedFlag> redFlags = redFlagEvaluator.evaluate(answered, answer);
        RedFlagSeverity urgency = redFlagEvaluator.overallSeverity(redFlags);

        Optional<ClinicalQuestion> next = questionPlanner.nextQuestion(answered.id());
        if (next.isPresent()) {
            ClinicalQuestion question = next.get();
            IntakeQuestion nextQuestion = conversationalWording(
                    IntakeQuestion.fromClinical(question),
                    answered.section().name(),
                    question.type().name(),
                    answer,
                    language,
                    result,
                    displayed);
            return new ClinicalIntakeResponse(nextQuestion,
                    DialogueState.IN_PROGRESS, false, redFlags, urgency, null, result.size(), totalQuestions);
        }

        DashavidhaQuestion firstDashavidha = dashavidhaQuestionPlanner.firstQuestion();
        IntakeQuestion nextQuestion = conversationalWording(
                IntakeQuestion.fromDashavidha(firstDashavidha),
                IntakeQuestion.DASHAVIDHA_SECTION,
                firstDashavidha.parameter().name(),
                answer,
                language,
                result,
                displayed);
        return new ClinicalIntakeResponse(nextQuestion,
                DialogueState.IN_PROGRESS, false, redFlags, urgency, null, result.size(), totalQuestions);
    }

    /**
     * Advances within the Dashavidha sequence. After the tenth parameter the
     * conversation transitions to the first Ahara-Vihara question rather than
     * completing.
     */
    private ClinicalIntakeResponse handleDashavidhaAnswer(String questionId, String answer,
                                                         AnswerSource answerSource,
                                                         SupportedLanguage language,
                                                         ClinicalConversationResult result,
                                                         Map<String, DisplayedQuestion> displayed) {
        DashavidhaQuestion answered = dashavidhaQuestionPlanner.question(questionId);
        recordAnswered(IntakeQuestion.fromDashavidha(answered), answer, answerSource, language, displayed, result);
        List<RedFlag> redFlags = redFlagEvaluator.evaluate(answer);
        RedFlagSeverity urgency = redFlagEvaluator.overallSeverity(redFlags);

        Optional<DashavidhaQuestion> next = dashavidhaQuestionPlanner.nextQuestion(answered.id());
        if (next.isPresent()) {
            DashavidhaQuestion question = next.get();
            IntakeQuestion nextQuestion = conversationalWording(
                    IntakeQuestion.fromDashavidha(question),
                    IntakeQuestion.DASHAVIDHA_SECTION,
                    question.parameter().name(),
                    answer,
                    language,
                    result,
                    displayed);
            return new ClinicalIntakeResponse(nextQuestion,
                    DialogueState.IN_PROGRESS, false, redFlags, urgency, null, result.size(), totalQuestions);
        }

        AharaViharaQuestion firstAharaVihara = aharaViharaQuestionPlanner.firstQuestion();
        IntakeQuestion nextQuestion = conversationalWording(
                IntakeQuestion.fromAharaVihara(firstAharaVihara),
                IntakeQuestion.AHARA_VIHARA_SECTION,
                firstAharaVihara.parameter().name(),
                answer,
                language,
                result,
                displayed);
        return new ClinicalIntakeResponse(nextQuestion,
                DialogueState.IN_PROGRESS, false, redFlags, urgency, null, result.size(), totalQuestions);
    }

    /**
     * Advances within the Ahara-Vihara sequence. After the eighth parameter the
     * whole intake conversation is complete and the case is persisted exactly
     * once for this HTTP session. The completion is guarded by a per-session
     * lock and a re-check of the completed-case marker, so a repeated or
     * concurrent final answer (double-click, refresh, back) can never persist
     * the same conversation twice.
     */
    private ClinicalIntakeResponse handleAharaViharaAnswer(String questionId, String answer,
                                                         AnswerSource answerSource,
                                                         SupportedLanguage language,
                                                         ClinicalConversationResult result,
                                                         Map<String, DisplayedQuestion> displayed,
                                                         HttpSession session,
                                                         ApplicationUserDetails principal) {
        synchronized (session.getId().intern()) {
            String alreadyCompleted = (String) session.getAttribute(COMPLETED_CASE_ATTRIBUTE);
            if (alreadyCompleted != null) {
                return completedResponse(alreadyCompleted);
            }

            AharaViharaQuestion answered = aharaViharaQuestionPlanner.question(questionId);
            recordAnswered(IntakeQuestion.fromAharaVihara(answered), answer, answerSource, language, displayed, result);
            List<RedFlag> redFlags = redFlagEvaluator.evaluate(answer);
            RedFlagSeverity urgency = redFlagEvaluator.overallSeverity(redFlags);

            Optional<AharaViharaQuestion> next = aharaViharaQuestionPlanner.nextQuestion(answered.id());
            if (next.isPresent()) {
                AharaViharaQuestion question = next.get();
                IntakeQuestion nextQuestion = conversationalWording(
                        IntakeQuestion.fromAharaVihara(question),
                        IntakeQuestion.AHARA_VIHARA_SECTION,
                        question.parameter().name(),
                        answer,
                        language,
                        result,
                        displayed);
                return new ClinicalIntakeResponse(nextQuestion,
                        DialogueState.IN_PROGRESS, false, redFlags, urgency, null, result.size(), totalQuestions);
            }

            return completeReady(session, result, principal, redFlags, urgency);
        }
    }

    /**
     * Persists the finished conversation as a completed case exactly once.
     *
     * <p>The completed-case marker is set inside the same per-session
     * lock that guards final-answer processing, so concurrent or repeated
     * completion requests see the marker and return the same case id without
     * re-persisting.</p>
     */
    private ClinicalIntakeResponse completeReady(HttpSession session,
                                                 ClinicalConversationResult result,
                                                 ApplicationUserDetails principal,
                                                 List<RedFlag> redFlags,
                                                 RedFlagSeverity urgency) {
        synchronized (session.getId().intern()) {
            String alreadyCompleted = (String) session.getAttribute(COMPLETED_CASE_ATTRIBUTE);
            if (alreadyCompleted != null) {
                return completedResponse(alreadyCompleted);
            }
            Long userId = principal != null ? principal.getId() : null;
            CompletedCase completedCase = reviewStore.register(result);
            casePersistence.save(completedCase, userId);
            session.setAttribute(COMPLETED_CASE_ATTRIBUTE, completedCase.id());
            if (userId != null) {
                patientSessionService.complete(userId);
            }
            return new ClinicalIntakeResponse(null, DialogueState.COMPLETED, true,
                    redFlags, urgency, completedCase.id(), totalQuestions, totalQuestions);
        }
    }

    /**
     * The canonical completed response for an already-completed session.
     */
    private ClinicalIntakeResponse completedResponse(String caseId) {
        return new ClinicalIntakeResponse(null, DialogueState.COMPLETED, true,
                List.of(), RedFlagSeverity.NONE, caseId, totalQuestions, totalQuestions);
    }

    /**
     * First question whose id is not yet present in the recorded answers, or
     * {@code null} when every plan is exhausted. Drives conversation resume
     * after a mid-session refresh.
     */
    private IntakeQuestion nextUnanswered(ClinicalConversationResult result) {
        Set<String> answered = result.all().stream()
                .map(ClinicalAnswer::questionId)
                .collect(Collectors.toSet());
        for (ClinicalQuestion q : questionPlanner.questions()) {
            if (!answered.contains(q.id())) {
                return IntakeQuestion.fromClinical(q);
            }
        }
        for (DashavidhaQuestion q : dashavidhaQuestionPlanner.questions()) {
            if (!answered.contains(q.id())) {
                return IntakeQuestion.fromDashavidha(q);
            }
        }
        for (AharaViharaQuestion q : aharaViharaQuestionPlanner.questions()) {
            if (!answered.contains(q.id())) {
                return IntakeQuestion.fromAharaVihara(q);
            }
        }
        return null;
    }

    private void restockDisplayed(HttpSession session, IntakeQuestion question, SupportedLanguage language) {
        Map<String, DisplayedQuestion> displayed = displayedFor(session);
        String translated = questionLocalization.translation(question.id(), language);
        if (translated != null) {
            displayed.put(question.id(), new DisplayedQuestion(translated, QuestionSource.TRANSLATED));
        } else {
            displayed.put(question.id(), new DisplayedQuestion(question.text(), QuestionSource.DETERMINISTIC));
        }
    }

    /**
     * Re-evaluates red flags deterministically across every recorded answer.
     */
    private List<RedFlag> flagsFor(ClinicalConversationResult result) {
        List<RedFlag> flags = new ArrayList<>();
        for (ClinicalAnswer answer : result.all()) {
            flags.addAll(redFlagEvaluator.evaluate(answer.answer()));
        }
        return List.copyOf(flags);
    }

    /**
     * Records the full clinical truth for one answered step: the canonical
     * question/objective, the wording that was actually displayed, its source
     * (translated, AI, or deterministic), the patient's verbatim answer, and the
     * patient-facing language used for this step.
     */
    private void recordAnswered(IntakeQuestion canonical,
                                String answer,
                                AnswerSource answerSource,
                                SupportedLanguage language,
                                Map<String, DisplayedQuestion> displayed,
                                ClinicalConversationResult result) {
        DisplayedQuestion shown = displayed.remove(canonical.id());
        if (shown == null) {
            shown = new DisplayedQuestion(canonical.text(), QuestionSource.DETERMINISTIC);
        }
        result.record(ClinicalAnswer.from(canonical, shown.text(), answer, shown.source(), answerSource,
                language.bcp47()));
    }

    /**
     * Lets a validated AI provider phrase the next deterministic question
     * naturally (or clarify the latest answer), always within the deterministic
     * objective. The question id, section, type, and ordering always come from
     * the deterministic planner; only the display text may differ. When AI is
     * unavailable or its output is rejected, the canonical deterministic text is
     * used unchanged. The resulting wording and source are remembered for the
     * next step so the clinical record reflects exactly what was shown.
     *
     * <p>AI rewording is only attempted for English. For a non-English session
     * the curated offline translation is used and recorded as
     * {@link QuestionSource#TRANSLATED}; no AI multilingual capability is
     * claimed.</p>
     */
    private IntakeQuestion conversationalWording(IntakeQuestion deterministic,
                                                 String sectionName,
                                                 String targetTopic,
                                                 String latestAnswer,
                                                 SupportedLanguage language,
                                                 ClinicalConversationResult result,
                                                 Map<String, DisplayedQuestion> displayed) {
        String translated = questionLocalization.translation(deterministic.id(), language);
        if (translated != null) {
            IntakeQuestion shown = new IntakeQuestion(deterministic.id(), deterministic.section(),
                    deterministic.type(), translated, deterministic.required(), deterministic.order());
            displayed.put(shown.id(), new DisplayedQuestion(shown.text(), QuestionSource.TRANSLATED));
            return shown;
        }
        NextQuestionWording wording = aiConversationService.nextQuestionWording(
                sectionName, targetTopic, deterministic.text(), latestAnswer, result.all(), language.bcp47());
        IntakeQuestion shown = deterministic;
        QuestionSource source = QuestionSource.DETERMINISTIC;
        if (wording.source() == NextQuestionSource.AI_GENERATED) {
            shown = new IntakeQuestion(deterministic.id(), deterministic.section(), deterministic.type(),
                    wording.text(), deterministic.required(), deterministic.order());
            source = QuestionSource.AI_GENERATED;
        }
        displayed.put(shown.id(), new DisplayedQuestion(shown.text(), source));
        return shown;
    }

    /** Wording actually shown to the patient for one question, and its source. */
    private record DisplayedQuestion(String text, QuestionSource source) {
    }
}