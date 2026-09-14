package in.devmedi.kiosk.module.clinical.adaptive;

import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Deterministic coverage engine for the adaptive complete-history
 * conversation.
 *
 * <p>It decides which question to ask next from the declarative bank using
 * only data-driven rules: applicability keywords found in earlier answers,
 * screening-parent gating (a song whose screening question was answered "no"
 * is skipped), and priority ordering. It never interprets medical meaning —
 * it only tracks coverage. This is the authoritative fallback when the AI
 * advisor is unavailable or disagrees.</p>
 */
@Component
public class AdaptiveConversationPlanner {

    private static final Set<String> NEGATIVES = Set.of("no", "none", "nil", "nothing", "nope",
            "never", "not", "don't", "dont", "do not", "no medicine", "no medicines",
            "no surgery", "no allergies", "no family", "no illness", "no illnesses", "no chronic");

    /**
     * Deterministically choose the next applicable question, or empty once
     * coverage is complete.
     */
    public Optional<AdaptiveHistoryQuestion> next(CompleteHistoryQuestionBank bank,
                                                  Map<String, String> answers) {
        List<AdaptiveHistoryQuestion> pending = new ArrayList<>(applicable(bank, answers));
        if (pending.isEmpty()) {
            return Optional.empty();
        }
        pending.sort(Comparator.comparing(AdaptiveHistoryQuestion::priority));
        return Optional.of(pending.get(0));
    }

    /**
     * All unanswered questions that are currently applicable given the answers
     * collected so far (includes keyword triggers and screening gating).
     */
    public List<AdaptiveHistoryQuestion> applicable(CompleteHistoryQuestionBank bank,
                                                    Map<String, String> answers) {
        Map<String, AdaptiveHistoryQuestion> byId = new LinkedHashMap<>();
        for (AdaptiveHistoryQuestion q : bank.questions()) {
            byId.put(q.id(), q);
        }

        Map<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : answers.entrySet()) {
            normalized.put(e.getKey(), e.getValue() == null ? "" : e.getValue().toLowerCase(Locale.ROOT));
        }

        List<AdaptiveHistoryQuestion> result = new ArrayList<>();
        for (AdaptiveHistoryQuestion q : bank.questions()) {
            if (answers.containsKey(q.id())) {
                continue;
            }
            if (isApplicable(q, byId, normalized)) {
                result.add(q);
            }
        }
        return result;
    }

    /** The set of history categories already touched by an answer. */
    public Set<String> coveredCategories(CompleteHistoryQuestionBank bank, Map<String, String> answers) {
        Map<String, AdaptiveHistoryQuestion> byId = byId(bank);
        Set<String> covered = new LinkedHashSet<>();
        for (String id : answers.keySet()) {
            AdaptiveHistoryQuestion q = byId.get(id);
            if (q != null) {
                covered.add(q.category().name());
            }
        }
        return covered;
    }

    /** Whether an explicit negative terminates the section for a screening question. */
    public static boolean isExplicitNegative(String answer) {
        if (answer == null) {
            return false;
        }
        String a = answer.trim().toLowerCase(Locale.ROOT);
        if (NEGATIVES.contains(a)) {
            return true;
        }
        return a.equals("no") || a.startsWith("no ") || a.startsWith("no,") || a.equals("none");
    }

    private boolean isApplicable(AdaptiveHistoryQuestion q,
                                 Map<String, AdaptiveHistoryQuestion> byId,
                                 Map<String, String> normalizedAnswers) {
        if (q.alwaysApplicable()) {
            return true;
        }
        // Keyword triggers anywhere in earlier answers (cross-category adaptation).
        for (String trigger : q.triggers()) {
            for (String value : normalizedAnswers.values()) {
                if (value.contains(trigger)) {
                    return true;
                }
            }
        }
        // Parent screening gating.
        if (q.parent() != null) {
            AdaptiveHistoryQuestion parent = byId.get(q.parent());
            if (parent != null) {
                String parentAnswer = normalizedAnswers.get(q.parent());
                if (parentAnswer != null) {
                    boolean parentNegative = parent.negativeTermination()
                            && isExplicitNegative(parentAnswer);
                    return !parentNegative;
                }
            }
        }
        return false;
    }

    private static Map<String, AdaptiveHistoryQuestion> byId(CompleteHistoryQuestionBank bank) {
        Map<String, AdaptiveHistoryQuestion> byId = new LinkedHashMap<>();
        for (AdaptiveHistoryQuestion q : bank.questions()) {
            byId.put(q.id(), q);
        }
        return byId;
    }

    /** Return the unanswered applicable questions as a compact hint list for the AI advisor. */
    public List<String> pendingIds(CompleteHistoryQuestionBank bank, Map<String, String> answers) {
        return applicable(bank, answers).stream().map(AdaptiveHistoryQuestion::id).toList();
    }

    /** Look up a question by id, or {@code null}. */
    @Nullable
    public AdaptiveHistoryQuestion byId(CompleteHistoryQuestionBank bank, String id) {
        return byId(bank).get(id);
    }
}