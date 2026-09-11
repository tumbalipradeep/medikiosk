package in.devmedi.kiosk.module.document.findings.model;

/**
 * Completeness of an extracted medication record relative to the fields that
 * matter for safe prescribing ({@link MedicationField}).
 *
 * <p>{@link #INCOMPLETE} only reports which important fields were never stated
 * in the source document; it never supplies a value for them.</p>
 */
public enum CompletenessStatus {
    COMPLETE,
    INCOMPLETE
}