package in.devmedi.kiosk.module.physician.workspace;

import in.devmedi.kiosk.module.document.findings.DocumentWorkspaceItem;

import java.time.Instant;
import java.util.List;

/**
 * Complete physician case workspace model, assembled from persisted case data,
 * persisted review decisions, deterministically re-derived flags, and existing
 * document/timeline data.
 */
public record WorkspaceView(String caseId,
                            Instant completedAt,
                            WorkspaceIdentity identity,
                            List<WorkspaceSection> sections,
                            int answeredCount,
                            int totalFlags,
                            String overallSeverityLabel,
                            List<DocumentWorkspaceItem> documents,
                            List<ConsentWorkspaceItem> consents,
                            InteropBoundaryView interoperability,
                            List<AuditWorkspaceItem> auditEvents,
                            String timelineUrl,
                            String fhirUrl,
                            boolean assigned,
                            String assigneeLabel,
                            boolean assignedToMe) {
}