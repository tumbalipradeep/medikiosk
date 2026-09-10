package in.devmedi.kiosk.module.clinical.controller;

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
import in.devmedi.kiosk.module.clinical.redflag.RedFlag;
import in.devmedi.kiosk.module.clinical.redflag.RedFlagEvaluator;
import in.devmedi.kiosk.module.clinical.redflag.RedFlagSeverity;
import in.devmedi.kiosk.module.physician.service.CompletedCaseReviewStore;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
 * kept in the HTTP session (nothing is persisted). When the intake completes,
 * that result is handed to the physician review store so a physician can review
 * the finished case in-memory. No AI providers are used.</p>
 */
@RestController
@RequestMapping("/patient/intake/conversation")
public class ClinicalIntakeConversationController {

    public static final String RESULT_ATTRIBUTE = "clinicalConversationResult";

    private final QuestionPlanner questionPlanner;
    private final DashavidhaQuestionPlanner dashavidhaQuestionPlanner;
    private final AharaViharaQuestionPlanner aharaViharaQuestionPlanner;
    private final RedFlagEvaluator redFlagEvaluator;
    private final CompletedCaseReviewStore reviewStore;

    private final Set<String> clinicalQuestionIds;
    private final Set<String> dashavidhaQuestionIds;
    private final Set<String> aharaViharaQuestionIds;

    public ClinicalIntakeConversationController(QuestionPlanner questionPlanner,
                                                DashavidhaQuestionPlanner dashavidhaQuestionPlanner,
                                                AharaViharaQuestionPlanner aharaViharaQuestionPlanner,
                                                RedFlagEvaluator redFlagEvaluator,
                                                CompletedCaseReviewStore reviewStore) {
        this.questionPlanner = questionPlanner;
        this.dashavidhaQuestionPlanner = dashavidhaQuestionPlanner;
        this.aharaViharaQuestionPlanner = aharaViharaQuestionPlanner;
        this.redFlagEvaluator = redFlagEvaluator;
        this.reviewStore = reviewStore;
        this.clinicalQuestionIds = questionPlanner.questions().stream()
                .map(ClinicalQuestion::id)
                .collect(Collectors.toUnmodifiableSet());
        this.dashavidhaQuestionIds = dashavidhaQuestionPlanner.questions().stream()
                .map(DashavidhaQuestion::id)
                .collect(Collectors.toUnmodifiableSet());
        this.aharaViharaQuestionIds = aharaViharaQuestionPlanner.questions().stream()
                .map(AharaViharaQuestion::id)
                .collect(Collectors.toUnmodifiableSet());
    }

    @PostMapping("/start")
    @ResponseStatus(HttpStatus.OK)
    public ClinicalIntakeResponse start(HttpSession session) {
        session.setAttribute(RESULT_ATTRIBUTE, new ClinicalConversationResult());
        IntakeQuestion first = IntakeQuestion.fromClinical(questionPlanner.firstQuestion());
        return new ClinicalIntakeResponse(first, DialogueState.IN_PROGRESS, false, List.of(), RedFlagSeverity.NONE);
    }

    @PostMapping("/answer")
    @ResponseStatus(HttpStatus.OK)
    public ClinicalIntakeResponse answer(@RequestBody ClinicalConversationAnswerRequest request,
                                         HttpSession session) {
        if (request == null || request.questionId() == null || request.questionId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "questionId is required");
        }
        ClinicalConversationResult result = resultFor(session);

        String questionId = request.questionId();
        if (clinicalQuestionIds.contains(questionId)) {
            return handleClinicalAnswer(questionId, request.answer(), result);
        }
        if (dashavidhaQuestionIds.contains(questionId)) {
            return handleDashavidhaAnswer(questionId, request.answer(), result);
        }
        if (aharaViharaQuestionIds.contains(questionId)) {
            return handleAharaViharaAnswer(questionId, request.answer(), result);
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown question id: " + questionId);
    }

    private ClinicalConversationResult resultFor(HttpSession session) {
        ClinicalConversationResult result = (ClinicalConversationResult) session.getAttribute(RESULT_ATTRIBUTE);
        if (result == null) {
            result = new ClinicalConversationResult();
            session.setAttribute(RESULT_ATTRIBUTE, result);
        }
        return result;
    }

    /**
     * Advances within the unchanged SOCRATES/HPI sequence. After the final HPI
     * question the conversation transitions to the first Dashavidha question
     * rather than completing.
     */
    private ClinicalIntakeResponse handleClinicalAnswer(String questionId, String answer,
                                                       ClinicalConversationResult result) {
        ClinicalQuestion answered = questionPlanner.question(questionId);
        result.record(ClinicalAnswer.from(IntakeQuestion.fromClinical(answered), answer));
        List<RedFlag> redFlags = redFlagEvaluator.evaluate(answered, answer);
        RedFlagSeverity urgency = redFlagEvaluator.overallSeverity(redFlags);

        Optional<ClinicalQuestion> next = questionPlanner.nextQuestion(answered.id());
        if (next.isPresent()) {
            ClinicalQuestion question = next.get();
            return new ClinicalIntakeResponse(IntakeQuestion.fromClinical(question),
                    DialogueState.IN_PROGRESS, false, redFlags, urgency);
        }

        DashavidhaQuestion firstDashavidha = dashavidhaQuestionPlanner.firstQuestion();
        return new ClinicalIntakeResponse(IntakeQuestion.fromDashavidha(firstDashavidha),
                DialogueState.IN_PROGRESS, false, redFlags, urgency);
    }

    /**
     * Advances within the Dashavidha sequence. After the tenth parameter the
     * conversation transitions to the first Ahara-Vihara question rather than
     * completing.
     */
    private ClinicalIntakeResponse handleDashavidhaAnswer(String questionId, String answer,
                                                          ClinicalConversationResult result) {
        DashavidhaQuestion answered = dashavidhaQuestionPlanner.question(questionId);
        result.record(ClinicalAnswer.from(IntakeQuestion.fromDashavidha(answered), answer));
        List<RedFlag> redFlags = redFlagEvaluator.evaluate(answer);
        RedFlagSeverity urgency = redFlagEvaluator.overallSeverity(redFlags);

        Optional<DashavidhaQuestion> next = dashavidhaQuestionPlanner.nextQuestion(answered.id());
        if (next.isPresent()) {
            DashavidhaQuestion question = next.get();
            return new ClinicalIntakeResponse(IntakeQuestion.fromDashavidha(question),
                    DialogueState.IN_PROGRESS, false, redFlags, urgency);
        }

        AharaViharaQuestion firstAharaVihara = aharaViharaQuestionPlanner.firstQuestion();
        return new ClinicalIntakeResponse(IntakeQuestion.fromAharaVihara(firstAharaVihara),
                DialogueState.IN_PROGRESS, false, redFlags, urgency);
    }

    /**
     * Advances within the Ahara-Vihara sequence. After the eighth parameter the
     * whole intake conversation is complete.
     */
    private ClinicalIntakeResponse handleAharaViharaAnswer(String questionId, String answer,
                                                           ClinicalConversationResult result) {
        AharaViharaQuestion answered = aharaViharaQuestionPlanner.question(questionId);
        result.record(ClinicalAnswer.from(IntakeQuestion.fromAharaVihara(answered), answer));
        List<RedFlag> redFlags = redFlagEvaluator.evaluate(answer);
        RedFlagSeverity urgency = redFlagEvaluator.overallSeverity(redFlags);

        Optional<AharaViharaQuestion> next = aharaViharaQuestionPlanner.nextQuestion(answered.id());
        if (next.isPresent()) {
            AharaViharaQuestion question = next.get();
            return new ClinicalIntakeResponse(IntakeQuestion.fromAharaVihara(question),
                    DialogueState.IN_PROGRESS, false, redFlags, urgency);
        }
        reviewStore.register(result);
        return new ClinicalIntakeResponse(null, DialogueState.COMPLETED, true, redFlags, urgency);
    }
}