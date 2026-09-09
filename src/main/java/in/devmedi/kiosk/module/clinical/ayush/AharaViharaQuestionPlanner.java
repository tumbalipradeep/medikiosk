package in.devmedi.kiosk.module.clinical.ayush;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Deterministic planner for the AYUSH Ahara-Vihara (diet and lifestyle)
 * assessment sequence.
 *
 * <p>Advances one question at a time through the eight diet and lifestyle
 * parameters, in a fixed stable order, after the ten Dashavidha parameters.
 * The sequence is fully offline and deterministic, independent of both the
 * SOCRATES/HPI planner and the Dashavidha planner. Questions are patient-facing
 * descriptive information gathering only — no diagnosis, dosha scoring, or
 * treatment recommendation is implied.</p>
 */
@Service
public class AharaViharaQuestionPlanner {

    private static final List<AharaViharaQuestion> SEQUENCE = List.of(
            question("ahara_vihara_ahara", AharaViharaParameter.AHARA,
                    "What do you usually eat and drink in a typical day?",
                    true,
                    1),
            question("ahara_vihara_meal_pattern", AharaViharaParameter.MEAL_PATTERN,
                    "What is your usual meal timing or meal pattern?",
                    true,
                    2),
            question("ahara_vihara_appetite", AharaViharaParameter.APPETITE,
                    "How would you describe your usual appetite?",
                    true,
                    3),
            question("ahara_vihara_hydration", AharaViharaParameter.HYDRATION,
                    "How much water or other fluids do you usually drink?",
                    true,
                    4),
            question("ahara_vihara_sleep", AharaViharaParameter.SLEEP,
                    "How many hours do you usually sleep, and how is your sleep?",
                    true,
                    5),
            question("ahara_vihara_physical_activity", AharaViharaParameter.PHYSICAL_ACTIVITY,
                    "How physically active are you during a usual day?",
                    true,
                    6),
            question("ahara_vihara_daily_routine", AharaViharaParameter.DAILY_ROUTINE,
                    "What does your usual daily routine look like?",
                    true,
                    7),
            question("ahara_vihara_habits", AharaViharaParameter.HABITS,
                    "Are there any regular lifestyle habits you would like to tell us about?",
                    true,
                    8));

    private final Map<String, AharaViharaQuestion> byId;

    public AharaViharaQuestionPlanner() {
        this.byId = SEQUENCE.stream()
                .collect(Collectors.toUnmodifiableMap(AharaViharaQuestion::id, Function.identity()));
    }

    /**
     * @return the first question of the Ahara-Vihara sequence (usual diet).
     */
    public AharaViharaQuestion firstQuestion() {
        return SEQUENCE.get(0);
    }

    /**
     * Returns exactly the next question after {@code lastAnsweredQuestionId},
     * or {@link Optional#empty()} when all eight parameters have been covered.
     *
     * @param lastAnsweredQuestionId id of the question just answered
     * @return the single next question, or empty after the eighth parameter
     */
    public Optional<AharaViharaQuestion> nextQuestion(String lastAnsweredQuestionId) {
        AharaViharaQuestion last = require(lastAnsweredQuestionId);
        int index = SEQUENCE.indexOf(last);
        return index + 1 < SEQUENCE.size() ? Optional.of(SEQUENCE.get(index + 1)) : Optional.empty();
    }

    /**
     * Convenience overload of {@link #nextQuestion(String)}.
     */
    public Optional<AharaViharaQuestion> nextQuestion(AharaViharaQuestion answered) {
        return nextQuestion(answered.id());
    }

    /**
     * @return the question with the given stable id
     * @throws IllegalArgumentException for an unknown question id
     */
    public AharaViharaQuestion question(String questionId) {
        return require(questionId);
    }

    /**
     * @return an unmodifiable view of the full deterministic Ahara-Vihara sequence.
     */
    public List<AharaViharaQuestion> questions() {
        return SEQUENCE;
    }

    private AharaViharaQuestion require(String questionId) {
        AharaViharaQuestion question = byId.get(questionId);
        if (question == null) {
            throw new IllegalArgumentException("Unknown Ahara-Vihara question id: " + questionId);
        }
        return question;
    }

    private static AharaViharaQuestion question(String id,
                                                AharaViharaParameter parameter,
                                                String text,
                                                boolean required,
                                                int order) {
        return new AharaViharaQuestion(id, parameter, text, required, order);
    }
}