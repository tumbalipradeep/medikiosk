package in.devmedi.kiosk.module.document.findings.analysis;

import in.devmedi.kiosk.module.document.findings.model.InteractionAnalysisStatus;
import in.devmedi.kiosk.module.document.findings.model.LabResult;
import in.devmedi.kiosk.module.document.findings.model.Medication;
import in.devmedi.kiosk.module.document.findings.model.StructuredFindings;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Deterministic interpretation layer applied on top of the raw structured
 * findings produced by M1.3:
 *
 * <ul>
 *   <li>lab values are compared against the document's own reference range
 *       ({@link LabAbnormalityEvaluator});</li>
 *   <li>medications are checked for completeness and duplicate occurrences
 *       ({@link MedicationFindingsAnalyzer});</li>
 *   <li>interaction analysis is explicitly {@link InteractionAnalysisStatus#NOT_AVAILABLE}
 *       because no reliable interaction dataset is shipped with this project.</li>
 * </ul>
 *
 * <p>Every step is deterministic, never invents data, never diagnoses, and
 * only enriches the existing parsed values without replacing them.</p>
 */
@Component
public class FindingsAnalysisService {

    private final MedicationFindingsAnalyzer medicationAnalyzer = new MedicationFindingsAnalyzer();

    public StructuredFindings analyze(StructuredFindings findings) {
        if (findings == null) {
            return null;
        }
        List<LabResult> labs = findings.labResults().stream()
                .map(lab -> lab.withAbnormality(
                        LabAbnormalityEvaluator.evaluate(lab.value(), lab.referenceRange())))
                .toList();
        List<Medication> medications = medicationAnalyzer.analyze(findings.medications());
        return new StructuredFindings(findings.patient(), findings.encounter(), findings.vitals(), labs,
                medications, InteractionAnalysisStatus.NOT_AVAILABLE);
    }
}