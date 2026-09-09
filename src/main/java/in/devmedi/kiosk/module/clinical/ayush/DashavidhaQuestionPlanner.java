package in.devmedi.kiosk.module.clinical.ayush;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Deterministic planner for the Dashavidha Pariksha assessment sequence.
 *
 * <p>Advances one question at a time through all ten classical parameters,
 * in the fixed order of {@link DashavidhaParameter}. The sequence is fully
 * offline and deterministic; it is independent of the existing SOCRATES/HPI
 * intake planner. The questions are patient-facing information gathering only —
 * no diagnosis, treatment, or scoring is implied.</p>
 */
@Service
public class DashavidhaQuestionPlanner {

    private static final List<DashavidhaQuestion> SEQUENCE = List.of(
            question("dashavidha_prakriti", DashavidhaParameter.PRAKRITI,
                    "Describe your general body constitution — for example, how you usually feel (such as hot or cold), "
                            + "your typical body frame, or the quality of your skin and hair.",
                    true,
                    1),
            question("dashavidha_vikriti", DashavidhaParameter.VIKRITI,
                    "Describe any current change or imbalance from your usual state of health related to your present problem.",
                    true,
                    2),
            question("dashavidha_sara", DashavidhaParameter.SARA,
                    "How would you describe your overall strength and stamina — and the general condition of your skin, "
                            + "nails, and body tissues?",
                    true,
                    3),
            question("dashavidha_samhanana", DashavidhaParameter.SAMHANANA,
                    "How would you describe your overall body frame and build — for example, thin, medium, or sturdy?",
                    true,
                    4),
            question("dashavidha_pramana", DashavidhaParameter.PRAMANA,
                    "How would you describe your height, weight, and body measurements compared with your usual self?",
                    false,
                    5),
            question("dashavidha_satmya", DashavidhaParameter.SATMYA,
                    "Which foods, drinks, and daily habits suit you well — and which tend not to agree with you?",
                    true,
                    6),
            question("dashavidha_sattva", DashavidhaParameter.SATTVA,
                    "How would you describe your usual mood, mental attitude, and the way you handle difficult situations?",
                    true,
                    7),
            question("dashavidha_ahara_shakti", DashavidhaParameter.AHARA_SHAKTI,
                    "How is your usual appetite and digestion — how well do you tolerate and digest regular meals?",
                    true,
                    8),
            question("dashavidha_vyayama_shakti", DashavidhaParameter.VYAYAMA_SHAKTI,
                    "How would you rate your physical endurance — for example, how long you can exercise or work before "
                            + "feeling tired?",
                    true,
                    9),
            question("dashavidha_vaya", DashavidhaParameter.VAYA,
                    "Which of these best describes your stage of life: childhood, youth, middle age, or old age?",
                    true,
                    10));

    private final Map<String, DashavidhaQuestion> byId;

    public DashavidhaQuestionPlanner() {
        this.byId = SEQUENCE.stream()
                .collect(Collectors.toUnmodifiableMap(DashavidhaQuestion::id, Function.identity()));
    }

    /**
     * @return the first question of the Dashavidha sequence (Prakriti).
     */
    public DashavidhaQuestion firstQuestion() {
        return SEQUENCE.get(0);
    }

    /**
     * Returns exactly the next question after {@code lastAnsweredQuestionId},
     * or {@link Optional#empty()} when all ten parameters have been covered.
     *
     * @param lastAnsweredQuestionId id of the question just answered
     * @return the single next question, or empty after the tenth parameter
     */
    public Optional<DashavidhaQuestion> nextQuestion(String lastAnsweredQuestionId) {
        DashavidhaQuestion last = require(lastAnsweredQuestionId);
        int index = SEQUENCE.indexOf(last);
        return index + 1 < SEQUENCE.size() ? Optional.of(SEQUENCE.get(index + 1)) : Optional.empty();
    }

    /**
     * Convenience overload of {@link #nextQuestion(String)}.
     */
    public Optional<DashavidhaQuestion> nextQuestion(DashavidhaQuestion answered) {
        return nextQuestion(answered.id());
    }

    /**
     * @return the question with the given stable id
     * @throws IllegalArgumentException for an unknown question id
     */
    public DashavidhaQuestion question(String questionId) {
        return require(questionId);
    }

    /**
     * @return an unmodifiable view of the full deterministic Dashavidha sequence.
     */
    public List<DashavidhaQuestion> questions() {
        return SEQUENCE;
    }

    private DashavidhaQuestion require(String questionId) {
        DashavidhaQuestion question = byId.get(questionId);
        if (question == null) {
            throw new IllegalArgumentException("Unknown Dashavidha question id: " + questionId);
        }
        return question;
    }

    private static DashavidhaQuestion question(String id,
                                               DashavidhaParameter parameter,
                                               String text,
                                               boolean required,
                                               int order) {
        return new DashavidhaQuestion(id, parameter, text, required, order);
    }
}