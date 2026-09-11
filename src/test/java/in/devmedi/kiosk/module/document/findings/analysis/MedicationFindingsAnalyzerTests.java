package in.devmedi.kiosk.module.document.findings.analysis;

import in.devmedi.kiosk.module.document.findings.model.CompletenessStatus;
import in.devmedi.kiosk.module.document.findings.model.Medication;
import in.devmedi.kiosk.module.document.findings.model.MedicationField;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MedicationFindingsAnalyzerTests {

    private final MedicationFindingsAnalyzer analyzer = new MedicationFindingsAnalyzer();

    private Medication med(String name, String dose, String route, String frequency, String duration) {
        return new Medication(name, "500 mg", dose, route, frequency, duration,
                "line", 0, null, null, 1);
    }

    @Test
    void medicationWithAllImportantFieldsIsComplete() {
        Medication result = analyzer.analyze(List.of(med("Metformin", "1", "Oral", "Twice a day", "30 days")))
                .get(0);
        assertThat(result.completeness()).isEqualTo(CompletenessStatus.COMPLETE);
        assertThat(result.missingFields()).isEmpty();
        assertThat(result.isDuplicate()).isFalse();
    }

    @Test
    void medicationMissingFieldsIsIncompleteAndListsThem() {
        Medication result = analyzer.analyze(List.of(med("Atorvastatin", null, "Oral", null, null))).get(0);
        assertThat(result.completeness()).isEqualTo(CompletenessStatus.INCOMPLETE);
        assertThat(result.missingFields()).containsExactlyInAnyOrder(
                MedicationField.DOSE, MedicationField.FREQUENCY, MedicationField.DURATION);
    }

    @Test
    void repeatedMedicationIsFlaggedAsDuplicate() {
        List<Medication> results = analyzer.analyze(List.of(
                med("Metformin", "1", "Oral", "Twice a day", "30 days"),
                med("metformin  ", "1", "Oral", "Twice a day", "30 days")));
        assertThat(results).allMatch(m -> m.duplicateCount() == 2);
        assertThat(results).allMatch(Medication::isDuplicate);
    }

    @Test
    void differentMedicationsAreNotDuplicates() {
        List<Medication> results = analyzer.analyze(List.of(
                med("Metformin", "1", "Oral", "Twice a day", "30 days"),
                med("Atorvastatin", "1", "Oral", "Night", "30 days"),
                med("Atorvastatin", "1", "Oral", "Night", "30 days")));
        assertThat(results.get(0).duplicateCount()).isEqualTo(1);
        assertThat(results.get(0).isDuplicate()).isFalse();
        assertThat(results.get(1).duplicateCount()).isEqualTo(2);
        assertThat(results.get(2).duplicateCount()).isEqualTo(2);
    }

    @Test
    void emptyMedicationListIsEmpty() {
        assertThat(analyzer.analyze(List.of())).isEmpty();
    }

    @Test
    void analysisIsDeterministic() {
        List<Medication> input = List.of(
                med("Metformin", "1", "Oral", "Twice a day", "30 days"),
                med("Metformin", "1", "Oral", "Twice a day", "30 days"),
                med("Cough Relief", null, null, "Twice a day", null));
        assertThat(analyzer.analyzeRepeated(input))
                .usingRecursiveFieldByFieldElementComparator()
                .isEqualTo(analyzer.analyze(input));
    }

    @Test
    void originalValuesAreNeverInventedOrReplaced() {
        Medication source = med("Cough Relief", null, "Oral", "Twice a day", null);
        Medication result = analyzer.analyze(List.of(source)).get(0);
        assertThat(result.dose()).isNull();
        assertThat(result.strength()).isEqualTo("500 mg");
        assertThat(result.route()).isEqualTo("Oral");
        assertThat(result.frequency()).isEqualTo("Twice a day");
    }
}