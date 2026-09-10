package in.devmedi.kiosk.module.physician.service;

import in.devmedi.kiosk.module.clinical.dialogue.ClinicalConversationResult;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Server-side in-memory handoff between the completed patient intake and the
 * physician review page.
 *
 * <p>When a patient finishes the 25-question intake conversation, the clinical
 * conversation controller registers the resulting
 * {@link ClinicalConversationResult} here. The store gives that result a stable
 * case identity and keeps the resulting {@link CompletedCase} for the physician
 * review page, which reads the most recently completed case instead of
 * generating its own demo data.
 *
 * <p>Nothing is persisted for this checkpoint: this is a single in-memory slot
 * holding the latest completed case for the lifetime of the application. The
 * case identity remains available for as long as the completed case is held
 * here.</p>
 */
@Service
public class CompletedCaseReviewStore {

    private volatile CompletedCase latest;

    /**
     * Registers a completed conversation as a new completed case, assigning it
     * a fresh, stable case identity and making it the latest case.
     *
     * @param completed the finished clinical conversation result
     * @return the completed case now held as the latest
     */
    public CompletedCase register(ClinicalConversationResult completed) {
        CompletedCase completedCase = CompletedCase.withNewId(completed);
        this.latest = completedCase;
        return completedCase;
    }

    /**
     * @return the most recently registered completed case, if any
     */
    public Optional<CompletedCase> latest() {
        return Optional.ofNullable(latest);
    }

    /**
     * Discards the held completed case (used for tests and fresh starts).
     */
    public void clear() {
        this.latest = null;
    }
}