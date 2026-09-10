package in.devmedi.kiosk.module.physician.service;

import in.devmedi.kiosk.module.clinical.dialogue.ClinicalConversationResult;

import java.util.UUID;

/**
 * A completed patient intake paired with a stable, in-memory case identity.
 *
 * <p>Wraps the finished {@link ClinicalConversationResult} with a unique id so
 * the physician review flow can reference an identifiable case instead of an
 * anonymous latest result. The id is generated once when the case is created
 * and stays stable while this case is held in the
 * {@link CompletedCaseReviewStore}. Nothing is persisted.</p>
 *
 * @param id     stable identifier for the completed case
 * @param result the completed clinical conversation
 */
public record CompletedCase(String id, ClinicalConversationResult result) {

    /**
     * Creates a completed case with a fresh, unique, stable id.
     *
     * @param result the completed clinical conversation
     * @return a new completed case with its own identity
     */
    public static CompletedCase withNewId(ClinicalConversationResult result) {
        return new CompletedCase("case-" + UUID.randomUUID(), result);
    }
}