package in.devmedi.kiosk.module.clinical.adaptive;

import in.devmedi.kiosk.module.ai.conversation.ConversationTurn;
import in.devmedi.kiosk.module.clinical.history.ClinicalHistoryService;
import in.devmedi.kiosk.module.clinical.history.HistoryDatum;
import in.devmedi.kiosk.module.clinical.provenance.ClinicalProvenance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Orchestrates the adaptive complete-clinical-history conversation.
 *
 * <p>The flow is strictly data-driven on top of the declarative bank and the
 * deterministic coverage planner. After each answer, the validated AI advisor
 * may pick the next question from the planner's currently-applicable set; when
 * the advisor is unavailable or unresolvable, the planner's own priority
 * ordering is used. Every answer is stored immediately into the structured
 * clinical history (idempotent upsert), so progress survives and replay does
 * not duplicate.</p>
 */
@Service
public class AdaptiveConversationService {

    private static final Logger log = LoggerFactory.getLogger(AdaptiveConversationService.class);
    private static final String SOURCE_LABEL = "adaptive complete-history conversation";

    private final CompleteHistoryQuestionBank bank;
    private final AdaptiveConversationPlanner planner;
    private final AdaptiveHistoryAdvisor advisor;
    private final ClinicalHistoryService historyService;
    private final AdaptiveConversationState state;

    public AdaptiveConversationService(CompleteHistoryQuestionBank bank,
                                       AdaptiveConversationPlanner planner,
                                       AdaptiveHistoryAdvisor advisor,
                                       ClinicalHistoryService historyService,
                                       AdaptiveConversationState state) {
        this.bank = bank;
        this.planner = planner;
        this.advisor = advisor;
        this.historyService = historyService;
        this.state = state;
    }

    /** Starts a fresh adaptive conversation for the given patient. */
    public AdaptiveView start(Long patientUserId) {
        state.begin();
        return advance(patientUserId);
    }

    /** Records the patient's answer for the current question and advances. */
    public AdaptiveView answer(Long patientUserId, String questionId, String answerText) {
        if (!state.isStarted() || state.isCompleted()) {
            throw new IllegalStateException("No adaptive conversation in progress");
        }
        if (questionId == null || !questionId.equals(state.getCurrentQuestionId())) {
            throw new IllegalArgumentException("Question out of sequence");
        }
        if (answerText == null || answerText.isBlank()) {
            throw new IllegalArgumentException("Answer cannot be blank");
        }
        AdaptiveHistoryQuestion q = bank.question(questionId);
        state.answer(questionId, answerText);
        persist(patientUserId, q, answerText);
        return advance(patientUserId);
    }

    /** The current question view (or a completed marker). */
    public AdaptiveView current(Long patientUserId) {
        if (!state.isStarted() || state.isCompleted()) {
            return AdaptiveView.none();
        }
        return view(false);
    }

    public boolean inProgress() {
        return state.isStarted() && !state.isCompleted();
    }

    private AdaptiveView advance(Long patientUserId) {
        List<AdaptiveHistoryQuestion> applicable = planner.applicable(bank, state.getAnswers());
        String advisorChoice = advisor.adviseNext(applicable, turns());
        if (advisorChoice != null) {
            AdaptiveHistoryQuestion picked = planner.byId(bank, advisorChoice);
            if (picked == null) {
                log.warn("Adaptive advisor returned an unplanned id '{}'; falling back to planner", advisorChoice);
            } else {
                state.setCurrentQuestionId(picked.id());
                return view(true);
            }
        }
        Optional<AdaptiveHistoryQuestion> next = planner.next(bank, state.getAnswers());
        if (next.isEmpty()) {
            state.complete();
            log.info("Adaptive complete-history conversation finished: answered={} patient={}",
                    state.getAnswers().size(), patientUserId);
            return view(false);
        }
        state.setCurrentQuestionId(next.get().id());
        return view(false);
    }

    private void persist(Long patientUserId, AdaptiveHistoryQuestion q, String answerText) {
        historyService.record(
                patientUserId,
                q.category(),
                q.conceptKey(),
                q.text(),
                answerText,
                null,
                ClinicalProvenance.PATIENT_REPORTED,
                SOURCE_LABEL);
    }

    private List<ConversationTurn> turns() {
        List<ConversationTurn> turns = new ArrayList<>();
        for (var entry : state.getAnswers().entrySet()) {
            AdaptiveHistoryQuestion q = planner.byId(bank, entry.getKey());
            if (q != null) {
                turns.add(new ConversationTurn(q.text(), entry.getValue()));
            }
        }
        return List.copyOf(turns);
    }

    private AdaptiveView view(boolean aiGuided) {
        boolean completed = state.isCompleted();
        return new AdaptiveView(
                state.isStarted(),
                completed,
                completed ? null : state.getCurrentQuestionId(),
                completed || state.getCurrentQuestionId() == null ? null
                        : bank.question(state.getCurrentQuestionId()).text(),
                completed || state.getCurrentQuestionId() == null ? null
                        : bank.question(state.getCurrentQuestionId()).category().readableName(),
                state.getAnswers().size(),
                planner.applicable(bank, state.getAnswers()).size(),
                aiGuided && !completed);
    }
}