package in.devmedi.kiosk.module.document.findings.model;

import java.util.List;

/**
 * One decoded medication line from a prescription.
 *
 * <p>Only fields explicitly present in the source are filled. Strength and
 * dose are both kept so the physician can see how the dose relates to the
 * formulation; a strength-less syrup line simply leaves strength blank.</p>
 *
 * @param name             drug name from the medication registry (or the
 *                         display name given after a dosage-form prefix)
 * @param strength         normalised strength, e.g. {@code 500 mg}, or {@code null}
 * @param dose             normalised dose, e.g. {@code 5 ml} or {@code 1}, or {@code null}
 * @param route            normalised route (e.g. {@code Oral}), or {@code null}
 * @param frequency        normalised frequency label (e.g. {@code Twice a day}), or {@code null}
 * @param duration         duration as printed, e.g. {@code 30 days}, or {@code null}
 * @param sourceSnippet    the full document line the medication was read from
 * @param occurrenceIndex  zero-based position among medications in document order
 * @param completeness     completeness status, or {@code null} before analysis
 * @param missingFields    important fields absent from the source, or {@code null} before analysis
 * @param duplicateCount   number of occurrences of the same normalised medication
 *                         in the document (1 or more)
 */
public record Medication(String name,
                         String strength,
                         String dose,
                         String route,
                         String frequency,
                         String duration,
                         String sourceSnippet,
                         int occurrenceIndex,
                         CompletenessStatus completeness,
                         List<MedicationField> missingFields,
                         int duplicateCount) {

    public Medication withAnalysis(CompletenessStatus completeness,
                                   List<MedicationField> missingFields,
                                   int duplicateCount) {
        return new Medication(name, strength, dose, route, frequency, duration,
                sourceSnippet, occurrenceIndex, completeness, missingFields, duplicateCount);
    }

    public boolean isDuplicate() {
        return duplicateCount > 1;
    }
}