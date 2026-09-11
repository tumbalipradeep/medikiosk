package in.devmedi.kiosk.module.audit.service;

import in.devmedi.kiosk.module.audit.entity.AuditEventType;
import in.devmedi.kiosk.module.audit.entity.AuditOutcome;

import java.time.Instant;

/**
 * Immutable description of one audit event to persist. Deliberately carries
 * identifiers and outcome only - never passwords, tokens, CSRF values, API
 * keys, clinical answer contents, document binaries, request bodies, or
 * generated FHIR Bundles.
 *
 * @param eventType     security-sensitive activity category
 * @param occurredAt    moment the activity happened
 * @param actorUsername authenticated username, or {@code null} when unknown
 * @param actorRole     authenticated role (e.g. {@code PHYSICIAN}), or {@code null}
 * @param caseId        completed-case identifier being acted upon, or {@code null}
 * @param operation     short operation label (e.g. {@code EXPORT})
 * @param resourceType  affected resource type (e.g. {@code Bundle})
 * @param outcome       terminal outcome
 * @param failureReason categorized failure reason, or {@code null} on success
 * @param requestPath   safe request metadata: the server-relative request URI
 */
public record AuditEventCommand(AuditEventType eventType,
                                Instant occurredAt,
                                String actorUsername,
                                String actorRole,
                                String caseId,
                                String operation,
                                String resourceType,
                                AuditOutcome outcome,
                                String failureReason,
                                String requestPath) {

    /** FHIR export activity (success or resolvable-case failure) at the physician boundary. */
    public static AuditEventCommand fhirExport(String actorUsername,
                                               String actorRole,
                                               String caseId,
                                               String requestPath,
                                               AuditOutcome outcome,
                                               String failureReason) {
        return new AuditEventCommand(AuditEventType.FHIR_EXPORT, Instant.now(), actorUsername,
                actorRole, caseId, "EXPORT", "Bundle", outcome, failureReason, requestPath);
    }
}