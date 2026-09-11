package in.devmedi.kiosk.module.document.findings.model;

/**
 * Clinically important medication fields used by completeness analysis.
 *
 * <p>Only fields whose presence can be confirmed from the source are ever
 * marked present; nothing is inferred.</p>
 */
public enum MedicationField {
    DOSE,
    FREQUENCY,
    ROUTE,
    DURATION
}