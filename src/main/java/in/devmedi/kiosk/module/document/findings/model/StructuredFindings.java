package in.devmedi.kiosk.module.document.findings.model;

import java.util.List;

/**
 * Everything the deterministic pipeline decoded from one document's extracted
 * text: the raw structured findings plus the deterministic interpretation
 * layer over them. Empty collections (never null) mean "nothing decoded".
 */
public record StructuredFindings(PatientIdentifiers patient,
                                 EncounterMetadata encounter,
                                 List<VitalSign> vitals,
                                 List<LabResult> labResults,
                                 List<Medication> medications,
                                 InteractionAnalysisStatus interactionAnalysisStatus) {

    public static StructuredFindings empty() {
        return new StructuredFindings(null, null, List.of(), List.of(), List.of(),
                InteractionAnalysisStatus.NOT_AVAILABLE);
    }
}