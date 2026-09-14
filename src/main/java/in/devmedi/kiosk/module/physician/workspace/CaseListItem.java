package in.devmedi.kiosk.module.physician.workspace;

import java.time.Instant;
import java.util.List;

/**
 * One row of the physician dashboard: a completed patient case with count
 * summaries derived deterministically from persisted data, plus its queue
 * state. A case is only private (assigned) when a physician holds its active
 * assignment; {@code assignedToMe} reflects the dashboard viewer's identity.
 */
public record CaseListItem(String caseId,
                           Instant completedAt,
                           String patientLabel,
                           boolean patientLinked,
                           String identityNote,
                           int answeredCount,
                           int flaggedCount,
                           int documentCount,
                           int reviewedCount,
                           boolean fullyReviewed,
                           List<String> languages,
                           boolean assigned,
                           String assigneeLabel,
                           boolean assignedToMe) {

    public CaseListItem(String caseId,
                        Instant completedAt,
                        String patientLabel,
                        boolean patientLinked,
                        String identityNote,
                        int answeredCount,
                        int flaggedCount,
                        int documentCount,
                        int reviewedCount,
                        boolean fullyReviewed,
                        List<String> languages) {
        this(caseId, completedAt, patientLabel, patientLinked, identityNote,
                answeredCount, flaggedCount, documentCount, reviewedCount, fullyReviewed,
                languages, false, "", false);
    }
}