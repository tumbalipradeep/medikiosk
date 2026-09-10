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
 * {@link ClinicalConversationResult} here. The physician review page then reads
 * the most recently completed case instead of generating its own demo data.
 *
 * <p>Nothing is persisted for this checkpoint: this is a single in-memory slot
 * holding the latest completed result for the lifetime of the application.</p>
 */
@Service
public class CompletedCaseReviewStore {

    private volatile ClinicalConversationResult latest;

    public void register(ClinicalConversationResult completed) {
        this.latest = completed;
    }

    public Optional<ClinicalConversationResult> latest() {
        return Optional.ofNullable(latest);
    }

    public void clear() {
        this.latest = null;
    }
}