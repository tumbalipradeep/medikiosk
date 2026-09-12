package in.devmedi.kiosk.module.physician.workspace;

import java.time.Instant;

/**
 * One audit event surfaced in the physician audit section.
 */
public record AuditWorkspaceItem(Instant occurredAt, String eventType, String operation,
                                 String outcome, String failureReason) {
}