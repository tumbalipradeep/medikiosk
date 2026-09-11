package in.devmedi.kiosk.module.document.findings.model;

/**
 * Deterministic lab-value abnormality classification.
 *
 * <p>This is a comparison result only: it never states what an abnormal value
 * implies for the patient, and it is never a diagnosis. {@link #UNKNOWN} means
 * the numeric value or the reference range could not be interpreted with
 * confidence, so no claim is made.</p>
 */
public enum AbnormalityStatus {
    LOW,
    NORMAL,
    HIGH,
    UNKNOWN
}