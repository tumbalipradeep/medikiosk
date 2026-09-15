package in.devmedi.kiosk.module.document.findings.model;

/**
 * Deterministic clinical timeline event type.
 *
 * <p>Every event type is backed by verifiable, persisted data only. No events
 * are synthesised from fabricated clinical information.</p>
 */
public enum TimelineEventType {
    DOCUMENT_UPLOADED,
    EXTRACTION_COMPLETED,
    REPORT_DATE,
    ENCOUNTER_DATE,
    VITALS,
    LAB_RESULT,
    ABNORMAL_LAB_DETECTED,
    MEDICATION,
    PHYSICIAN_REVIEW,
    ENCOUNTER_CREATED,
    CONSULTATION_FINALIZED
}