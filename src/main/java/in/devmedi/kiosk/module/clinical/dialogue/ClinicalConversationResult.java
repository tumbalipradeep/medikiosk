package in.devmedi.kiosk.module.clinical.dialogue;

import java.util.ArrayList;
import java.util.List;

/**
 * In-memory holder for the answers of one patient intake conversation.
 *
 * <p>Keeps every answered clinical question in the order it was answered. It is
 * deliberately not persisted; it lives only in the HTTP session of the running
 * application during one conversation. No AI, scoring, or summarization is
 * performed here.</p>
 */
public final class ClinicalConversationResult {

    private final List<ClinicalAnswer> answers = new ArrayList<>();

    /**
     * Appends one answered question to the conversation in order.
     *
     * @param answer the structured answer to record
     */
    public void record(ClinicalAnswer answer) {
        answers.add(answer);
    }

    /**
     * @return an unmodifiable snapshot of all answers, in answering order
     */
    public List<ClinicalAnswer> all() {
        return List.copyOf(answers);
    }

    /**
     * @return how many questions have been answered so far
     */
    public int size() {
        return answers.size();
    }

    /**
     * @return whether no questions have been answered yet
     */
    public boolean isEmpty() {
        return answers.isEmpty();
    }
}