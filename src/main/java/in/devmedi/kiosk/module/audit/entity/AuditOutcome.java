package in.devmedi.kiosk.module.audit.entity;

/**
 * Terminal outcome of an audited operation. {@link #FAILURE} events carry an
 * optional categorized failure reason (e.g. {@code CASE_NOT_FOUND}) but never
 * clinical details.
 */
public enum AuditOutcome {
    SUCCESS,
    FAILURE
}