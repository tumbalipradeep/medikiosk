package in.devmedi.kiosk.module.clinical.redflag;

import in.devmedi.kiosk.module.clinical.dialogue.ClinicalQuestion;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Deterministic, offline safety net for the patient intake conversation.
 *
 * <p>Evaluates the patient's free-text answer against a small fixed set of
 * explicit, conservative warning patterns and returns zero or more
 * {@link RedFlag}s. It never calls an external AI API and never produces a
 * diagnosis or treatment recommendation.</p>
 *
 * <p><strong>Demo safety rules only.</strong> These patterns cover a handful of
 * obvious emergency-style expressions relevant to the intake demo. They are not
 * a complete medical triage system and intentionally stay conservative so that
 * vague ordinary wording does not raise an urgent flag.</p>
 */
@Service
public class RedFlagEvaluator {

    private static final int CASE_INSENSITIVE = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    private static final List<RedFlagRule> RULES = List.of(
            new RedFlagRule(
                    "red_flag_breathing",
                    RedFlagSeverity.URGENT,
                    "Severe breathing difficulty",
                    "Severe difficulty breathing or inability to breathe needs urgent attention.",
                    Pattern.compile(
                            "\\b(cannot|can'?t|cant|unable to|struggling to|hard to) breathe\\b"
                                    + "|\\bsevere difficulty breathing\\b",
                            CASE_INSENSITIVE)),
            new RedFlagRule(
                    "red_flag_consciousness",
                    RedFlagSeverity.URGENT,
                    "Loss of consciousness / fainting",
                    "Loss of consciousness or fainting needs urgent attention.",
                    Pattern.compile(
                            "\\b(blacked\\s*out|fainted|fainting|lost\\s+consciousness"
                                    + "|loss\\s+of\\s+consciousness|went\\s+unconscious|unconscious)\\b",
                            CASE_INSENSITIVE)),
            new RedFlagRule(
                    "red_flag_chest_pain",
                    RedFlagSeverity.URGENT,
                    "Severe chest pain or pressure",
                    "Severe chest pain or pressure needs urgent attention.",
                    Pattern.compile(
                            "\\b(severe|crushing|crippling|worst|squeezing)\\s+chest\\s+(pain|pressure|tightness)\\b",
                            CASE_INSENSITIVE)),
            new RedFlagRule(
                    "red_flag_bleeding",
                    RedFlagSeverity.URGENT,
                    "Uncontrolled heavy bleeding",
                    "Uncontrolled or heavy bleeding needs urgent attention.",
                    Pattern.compile(
                            "\\b(uncontrolled|uncontrollable|heavy|profuse|gushing|severe)\\s+bleeding\\b"
                                    + "|\\b(bleeding|bleed(ing)?)\\s+(heavily|profusely|uncontrollably|non-?stop)\\b",
                            CASE_INSENSITIVE)),
            new RedFlagRule(
                    "red_flag_stroke",
                    RedFlagSeverity.URGENT,
                    "Stroke-like weakness",
                    "Sudden inability to move one side or stroke-like weakness needs urgent attention.",
                    Pattern.compile(
                            "\\b(can'?t|cannot|couldn'?t|could not|unable to)\\s+(move|use)"
                                    + "\\s+((the|my|his|her|one)\\s+)?((left|right)\\s+)?(arm|leg|hand|side|foot)\\b"
                                    + "|\\b(sudden|suddenly)\\s+(weakness|paralysis|numbness)\\b"
                                    + "|\\b(droop(ing)?)\\s+(face|eyelid)\\b"
                                    + "|\\b(face|eyelid)\\s+(is\\s+)?droop(ing)?\\b",
                            CASE_INSENSITIVE)));

    /**
     * Evaluates an answer for red flags.
     *
     * @param question the clinical question that prompted this answer (may guide future rules)
     * @param answer   the patient's free-text answer
     * @return zero or more red flags, in deterministic rule order
     */
    public List<RedFlag> evaluate(ClinicalQuestion question, String answer) {
        if (answer == null || answer.isBlank()) {
            return List.of();
        }
        String normalized = normalize(answer);
        List<RedFlag> flags = new ArrayList<>();
        for (RedFlagRule rule : RULES) {
            if (rule.pattern().matcher(normalized).find()) {
                flags.add(new RedFlag(rule.id(), rule.severity(), rule.title(), rule.message()));
            }
        }
        return List.copyOf(flags);
    }

    /**
     * Evaluates an answer for red flags when the prompting question is not a
     * clinical (HPI/SOCRATES) question — for example a Dashavidha answer.
     * The same deterministic rules apply.
     *
     * @param answer the patient's free-text answer
     * @return zero or more red flags, in deterministic rule order
     */
    public List<RedFlag> evaluate(String answer) {
        return evaluate((ClinicalQuestion) null, answer);
    }

    /**
     * @return the highest severity present in {@code flags}, or {@link RedFlagSeverity#NONE}.
     */
    public RedFlagSeverity overallSeverity(List<RedFlag> flags) {
        if (flags == null || flags.isEmpty()) {
            return RedFlagSeverity.NONE;
        }
        for (RedFlag flag : flags) {
            if (flag.severity() == RedFlagSeverity.URGENT) {
                return RedFlagSeverity.URGENT;
            }
        }
        return RedFlagSeverity.NONE;
    }

    /**
     * Safely normalizes an answer for matching: trims, lowercases, and collapses
     * whitespace. No stemming or AI interpretation is applied.
     */
    String normalize(String answer) {
        return answer.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }
}