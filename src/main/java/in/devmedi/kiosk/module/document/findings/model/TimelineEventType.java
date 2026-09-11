package in.devmedi.kiosk.module.document.findings.model;

/**
 * Deterministic clinical timeline event type.
 *
 * <p>Every event type is backed by verifiable, persisted data only. No events
 * are synthesised from fabricated clinical information.</p>
 */
public enum TimelineEventType {
    DOCUMENT_UPLOADED,
    REPORT_DATE,
    ENCOUNTER_DATE,
    VITALS,
    LAB_RESULT,
    MEDICATION
}