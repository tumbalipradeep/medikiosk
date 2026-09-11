package in.devmedi.kiosk.module.document.findings.model;

/**
 * Encounter/report metadata decoded from a document's header block.
 *
 * <p>Each value is {@code null} when the field was not present or could not be
 * safely decoded - nothing is guessed.</p>
 *
 * @param reportDate    normalised report date (day-first or named-month), or {@code null}
 * @param encounterDate normalised encounter/visit/sample date, or {@code null}
 * @param facility      facility/hospital/clinic name, or {@code null}
 * @param clinician     referring/treating clinician name, or {@code null}
 * @param reportType    report type (label or recognised section heading), or {@code null}
 */
public record EncounterMetadata(String reportDate,
                                String encounterDate,
                                String facility,
                                String clinician,
                                String reportType) {
}