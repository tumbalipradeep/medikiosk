package in.devmedi.kiosk.module.document.findings.parser;

import in.devmedi.kiosk.module.document.findings.model.EncounterMetadata;
import in.devmedi.kiosk.module.document.findings.model.InteractionAnalysisStatus;
import in.devmedi.kiosk.module.document.findings.model.LabResult;
import in.devmedi.kiosk.module.document.findings.model.Medication;
import in.devmedi.kiosk.module.document.findings.model.PatientIdentifiers;
import in.devmedi.kiosk.module.document.findings.model.StructuredFindings;
import in.devmedi.kiosk.module.document.findings.model.VitalSign;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Production {@link ClinicalFindingsParser} built from fully deterministic,
 * rule-based sub-parsers. It never calls an LLM, never throws, and returns the
 * same {@link StructuredFindings} for the same input.
 */
@Component
public class DeterministicClinicalFindingsParser implements ClinicalFindingsParser {

    private final PatientParser patientParser = new PatientParser();
    private final EncounterParser encounterParser = new EncounterParser();
    private final VitalsParser vitalsParser = new VitalsParser();
    private final LabResultsParser labResultsParser;
    private final MedicationsParser medicationsParser;

    public DeterministicClinicalFindingsParser(LabTestRegistry labTestRegistry,
                                               MedicationNameRegistry medicationNameRegistry) {
        this.labResultsParser = new LabResultsParser(labTestRegistry);
        this.medicationsParser = new MedicationsParser(medicationNameRegistry);
    }

    @Override
    public StructuredFindings parse(String extractedText) {
        if (extractedText == null || extractedText.isBlank()) {
            return StructuredFindings.empty();
        }
        List<String> lines = extractedText.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .toList();
        if (lines.isEmpty()) {
            return StructuredFindings.empty();
        }

        PatientIdentifiers patient = patientParser.parse(lines);
        EncounterMetadata encounter = encounterParser.parse(lines);
        List<VitalSign> vitals = vitalsParser.parse(lines);
        List<LabResult> labs = labResultsParser.parse(lines);
        List<Medication> medications = medicationsParser.parse(lines);

        return new StructuredFindings(patient, encounter, vitals, labs, medications,
                InteractionAnalysisStatus.NOT_AVAILABLE);
    }
}