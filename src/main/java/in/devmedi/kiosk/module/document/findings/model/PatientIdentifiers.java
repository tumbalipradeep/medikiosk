package in.devmedi.kiosk.module.document.findings.model;

/**
 * Patient identity details decoded from a document's header block.
 *
 * <p>Each value is {@code null} when the field was not present or could not be
 * safely decoded - nothing is guessed.</p>
 *
 * @param name        patient name as printed
 * @param dateOfBirth normalised date of birth (day-first or named-month), or {@code null}
 * @param sex         normalised sex ({@code Male}/{@code Female}), or {@code null}
 * @param mrn         medical record number, or {@code null}
 */
public record PatientIdentifiers(String name,
                                 String dateOfBirth,
                                 String sex,
                                 String mrn) {
}