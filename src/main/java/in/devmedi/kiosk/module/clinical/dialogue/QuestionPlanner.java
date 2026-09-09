package in.devmedi.kiosk.module.clinical.dialogue;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Deterministic planner for the clinical intake dialogue.
 *
 * <p>Advances one question at a time through a fixed SOCRATES-style sequence
 * for the chief complaint ({@link ClinicalSection#CHIEF_COMPLAINT}) and the
 * history of present illness ({@link ClinicalSection#HISTORY_OF_PRESENT_ILLNESS}).
 * No AI providers are used; the sequence is fully offline and deterministic.</p>
 */
@Service
public class QuestionPlanner {

    private static final List<ClinicalQuestion> SEQUENCE = List.of(
            new ClinicalQuestion(
                    "chief_complaint_symptom",
                    ClinicalSection.CHIEF_COMPLAINT,
                    QuestionType.SYMPTOM_PROBLEM,
                    "What is the main problem or symptom that brought you here today?",
                    true,
                    1),
            new ClinicalQuestion(
                    "hpi_onset",
                    ClinicalSection.HISTORY_OF_PRESENT_ILLNESS,
                    QuestionType.ONSET,
                    "When did it first start?",
                    true,
                    2),
            new ClinicalQuestion(
                    "hpi_provocation_palliation",
                    ClinicalSection.HISTORY_OF_PRESENT_ILLNESS,
                    QuestionType.PROVOCATION_PALLIATION,
                    "What makes it better or worse?",
                    true,
                    3),
            new ClinicalQuestion(
                    "hpi_quality",
                    ClinicalSection.HISTORY_OF_PRESENT_ILLNESS,
                    QuestionType.QUALITY,
                    "How would you describe the feeling or quality of the problem?",
                    true,
                    4),
            new ClinicalQuestion(
                    "hpi_region_radiation",
                    ClinicalSection.HISTORY_OF_PRESENT_ILLNESS,
                    QuestionType.REGION_RADIATION,
                    "Where exactly is it, and does it spread anywhere else?",
                    true,
                    5),
            new ClinicalQuestion(
                    "hpi_severity",
                    ClinicalSection.HISTORY_OF_PRESENT_ILLNESS,
                    QuestionType.SEVERITY,
                    "On a scale of 0 to 10, how severe is it right now?",
                    false,
                    6),
            new ClinicalQuestion(
                    "hpi_timing_duration",
                    ClinicalSection.HISTORY_OF_PRESENT_ILLNESS,
                    QuestionType.TIMING_DURATION,
                    "Is it constant or does it come and go? How long does each episode last?",
                    true,
                    7));

    private final Map<String, ClinicalQuestion> byId;

    public QuestionPlanner() {
        this.byId = SEQUENCE.stream()
                .collect(Collectors.toUnmodifiableMap(ClinicalQuestion::id, Function.identity()));
    }

    /**
     * @return the first question of the dialogue (chief complaint, symptom/problem).
     */
    public ClinicalQuestion firstQuestion() {
        return SEQUENCE.get(0);
    }

    /**
     * Returns exactly the next question after {@code lastAnsweredQuestionId},
     * or {@link Optional#empty()} when the dialogue sequence is complete.
     *
     * @param lastAnsweredQuestionId id of the question just answered
     * @return the single next question, or empty after the final question
     */
    public Optional<ClinicalQuestion> nextQuestion(String lastAnsweredQuestionId) {
        ClinicalQuestion last = require(lastAnsweredQuestionId);
        int index = SEQUENCE.indexOf(last);
        return index + 1 < SEQUENCE.size() ? Optional.of(SEQUENCE.get(index + 1)) : Optional.empty();
    }

    /**
     * Convenience overload of {@link #nextQuestion(String)}.
     */
    public Optional<ClinicalQuestion> nextQuestion(ClinicalQuestion answered) {
        return nextQuestion(answered.id());
    }

    /**
     * Looks up a question by its stable id.
     *
     * @param questionId stable question id
     * @return the matching question
     * @throws IllegalArgumentException if the id is unknown
     */
    public ClinicalQuestion question(String questionId) {
        return require(questionId);
    }

    /**
     * @return the current dialogue state for the given last answered question id.
     */
    public DialogueState state(String lastAnsweredQuestionId) {
        if (lastAnsweredQuestionId == null || lastAnsweredQuestionId.isBlank()) {
            return DialogueState.NOT_STARTED;
        }
        ClinicalQuestion last = require(lastAnsweredQuestionId);
        return SEQUENCE.indexOf(last) == SEQUENCE.size() - 1 ? DialogueState.COMPLETED : DialogueState.IN_PROGRESS;
    }

    /**
     * @return an unmodifiable view of the full deterministic question sequence.
     */
    public List<ClinicalQuestion> questions() {
        return SEQUENCE;
    }

    private ClinicalQuestion require(String questionId) {
        ClinicalQuestion question = byId.get(questionId);
        if (question == null) {
            throw new IllegalArgumentException("Unknown clinical question id: " + questionId);
        }
        return question;
    }
}