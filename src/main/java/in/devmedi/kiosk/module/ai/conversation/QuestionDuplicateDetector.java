package in.devmedi.kiosk.module.ai.conversation;

import java.util.List;
import java.util.Set;

/**
 * Deterministic, intentionally conservative duplicate detection for AI-proposed
 * questions.
 *
 * <p>The detector compares a candidate question against the canonical texts of
 * questions that have already been asked (and answered) in the conversation.
 * Two overlapping rules are used, both purely lexical:</p>
 *
 * <ul>
 *   <li>Jaccard overlap of the normalized token bags above the threshold, or</li>
 *   <li>full containment of the candidate's tokens inside an earlier question</li>
 * </ul>
 *
 * <p>There is no NLP, stemming, or synonym handling. When the candidate is too
 * short to judge, or when there is any ambiguity, the detector does not claim a
 * duplicate — the surrounding objective-boundary validation still protects the
 * conversation, and any uncertainty intentionally errs toward the conservative
 * side by returning {@code false} if it cannot establish a match with the
 * canonical texts it was given.</p>
 *
 * <p>The callers additionally guard on the structured {@code topic} anchor, so
 * this class only becomes relevant when the model rephrases something whose
 * plain words already match an earlier question.</p>
 */
public final class QuestionDuplicateDetector {

    /** Minimum Jaccard overlap that alone marks a pair as semantically repeated. */
    public static final double JACCARD_THRESHOLD = 0.55;

    /** Minimum size of a canonical question's token bag before containment is judged. */
    public static final int MIN_ASKED_TOKENS_FOR_CONTAINMENT = 4;

    /** Minimum size of the candidate's token bag before containment is judged. */
    public static final int MIN_CANDIDATE_TOKENS = 2;

    public boolean isDuplicate(String candidate, List<String> alreadyAskedQuestions) {
        if (candidate == null || alreadyAskedQuestions == null || alreadyAskedQuestions.isEmpty()) {
            return false;
        }
        String trimmed = candidate.trim();
        for (String asked : alreadyAskedQuestions) {
            if (asked == null) {
                continue;
            }
            if (asked.trim().equalsIgnoreCase(trimmed)) {
                return true;
            }
            if (overlaps(trimmed, asked)) {
                return true;
            }
        }
        return false;
    }

    private boolean overlaps(String candidate, String asked) {
        Set<String> candidateTokens = QuestionTokens.tokensOf(candidate);
        Set<String> askedTokens = QuestionTokens.tokensOf(asked);
        if (candidateTokens.isEmpty() || askedTokens.isEmpty()) {
            return false;
        }
        if (candidateTokens.size() < MIN_CANDIDATE_TOKENS) {
            return false;
        }
        Set<String> smaller = candidateTokens;
        Set<String> larger = askedTokens;
        if (smaller.size() > larger.size()) {
            Set<String> swap = larger;
            larger = smaller;
            smaller = swap;
        }
        int intersection = 0;
        for (String token : smaller) {
            if (larger.contains(token)) {
                intersection++;
            }
        }
        int union = smaller.size() + larger.size() - intersection;
        if ((double) intersection / union >= JACCARD_THRESHOLD) {
            return true;
        }
        if (askedTokens.size() >= MIN_ASKED_TOKENS_FOR_CONTAINMENT
                && candidateTokens.size() >= MIN_CANDIDATE_TOKENS
                && askedTokens.containsAll(candidateTokens)) {
            return true;
        }
        if (candidateTokens.size() >= 5
                && askedTokens.size() >= MIN_CANDIDATE_TOKENS
                && candidateTokens.containsAll(askedTokens)) {
            return true;
        }
        return false;
    }
}