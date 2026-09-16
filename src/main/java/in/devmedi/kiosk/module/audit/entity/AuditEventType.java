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

    /** Physician FHIR export through the interoperability boundary. */
    FHIR_EXPORT,

    /** Physician review decision (accept / amend / reject) against an intake answer. */
    PHYSICIAN_REVIEW,

    /**
     * Patient submitted a correction against a captured intake answer of their
     * own case. Corrections are additive evidence: the original answer row is
     * never modified, and this event records who corrected what, when.
     */
    PATIENT_CORRECTION,

    /**
     * An administrator changed or removed a runtime platform setting through
     * the control center. Carries the setting key (never the value, which may
     * be operationally sensitive) so configuration changes stay auditable.
     */
    SETTINGS_CHANGE
}