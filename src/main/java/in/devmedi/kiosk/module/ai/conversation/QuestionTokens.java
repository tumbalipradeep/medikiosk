package in.devmedi.kiosk.module.ai.conversation;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Deterministic tokenization of free-text questions for the conservative
 * duplicate/objective checks. No stemming, no synonyms, no NLP: just a stable,
 * testable word bag that strips a fixed set of connective/function words.
 */
final class QuestionTokens {

    private static final Pattern SPLIT = Pattern.compile("[^a-z0-9]+");
    private static final Set<String> STOPWORDS = Set.of(
            "a", "an", "the", "and", "or", "but", "nor", "so", "of", "to", "in", "on",
            "at", "for", "with", "by", "from", "into", "about", "between",
            "you", "your", "yours", "it", "its", "me", "my", "i", "we", "our",
            "is", "are", "was", "were", "be", "been", "being", "have", "has", "had");

    private QuestionTokens() {
    }

    /**
     * @return the normalized token set of a question; empty when no meaningful tokens remain
     */
    static Set<String> tokensOf(String text) {
        if (text == null) {
            return Set.of();
        }
        String lower = text.toLowerCase(Locale.ROOT).trim();
        if (lower.isEmpty()) {
            return Set.of();
        }
        Set<String> tokens = new LinkedHashSet<>();
        for (String part : SPLIT.split(lower)) {
            if (part.isEmpty() || part.length() == 1 || STOPWORDS.contains(part)) {
                continue;
            }
            tokens.add(part);
        }
        return Set.copyOf(tokens);
    }
}