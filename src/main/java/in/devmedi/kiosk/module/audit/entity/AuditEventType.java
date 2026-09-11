package in.devmedi.kiosk.module.audit.entity;

/**
 * Categories of security-sensitive activity recorded in the audit trail.
 *
 * <p>{@link #FHIR_EXPORT} covers physician FHIR export of a completed case
 * through the interoperability boundary. Event types for future ABDM
 * integration (e.g. consent grants or transmission attempts) would be added
 * here as dedicated values rather than folding them into {@code FHIR_EXPORT}.</p>
 */
public enum AuditEventType {
    FHIR_EXPORT
}