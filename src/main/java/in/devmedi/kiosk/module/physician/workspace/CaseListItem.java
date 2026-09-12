package in.devmedi.kiosk.module.physician.workspace;

import java.time.Instant;
import java.util.List;

/**
 * One row of the physician dashboard: a completed patient case with count
 * summaries derived deterministically from persisted data.
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
                           List<String> languages) {
}