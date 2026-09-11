package in.devmedi.kiosk.module.document.findings.analysis;

import in.devmedi.kiosk.module.document.findings.model.CompletenessStatus;
import in.devmedi.kiosk.module.document.findings.model.Medication;
import in.devmedi.kiosk.module.document.findings.model.MedicationField;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Deterministic interpretation of extracted medications: completeness against
 * the important prescribing fields and duplicate-occurrence detection.
 *
 * <p>Completeness only reports which important fields the source never stated;
 * it never infers a value for a missing field. Duplicates group medications by
 * their normalised (lower-cased, whitespace-collapsed) name, so two rows of
 * «Metformin» are flagged while «Metformin» and «Atorvastatin» stay distinct.</p>
 */
public final class MedicationFindingsAnalyzer {

    private final List<MedicationField> importantFields =
            List.of(MedicationField.DOSE, MedicationField.FREQUENCY, MedicationField.ROUTE, MedicationField.DURATION);

    /**
     * @see #analyzeRepeated(List)
     */
    public List<Medication> analyze(List<Medication> medications) {
        Map<String, Integer> counts = new HashMap<>();
        for (Medication medication : medications) {
            counts.merge(normalizedName(medication.name()), 1, Integer::sum);
        }

        List<Medication> analyzed = new ArrayList<>(medications.size());
        for (Medication medication : medications) {
            List<MedicationField> missing = missingFields(medication);
            CompletenessStatus completeness = missing.isEmpty() ? CompletenessStatus.COMPLETE
                    : CompletenessStatus.INCOMPLETE;
            analyzed.add(medication.withAnalysis(completeness, missing,
                    counts.get(normalizedName(medication.name()))));
        }
        return List.copyOf(analyzed);
    }

    /**
     * Repeated analysis is deterministic for identical inputs.
     */
    public List<Medication> analyzeRepeated(List<Medication> medications) {
        List<Medication> first = analyze(medications);
        if (!first.equals(analyze(medications))) {
            throw new IllegalStateException("medication analysis is not deterministic");
        }
        return first;
    }

    private List<MedicationField> missingFields(Medication medication) {
        List<MedicationField> missing = new ArrayList<>();
        if (!hasText(medication.dose())) {
            missing.add(MedicationField.DOSE);
        }
        if (!hasText(medication.frequency())) {
            missing.add(MedicationField.FREQUENCY);
        }
        if (!hasText(medication.route())) {
            missing.add(MedicationField.ROUTE);
        }
        if (!hasText(medication.duration())) {
            missing.add(MedicationField.DURATION);
        }
        return List.copyOf(missing);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String normalizedName(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }
}