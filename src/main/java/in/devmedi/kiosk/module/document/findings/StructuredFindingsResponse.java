package in.devmedi.kiosk.module.document.findings;

import in.devmedi.kiosk.module.document.extraction.ExtractionErrorCategory;
import in.devmedi.kiosk.module.document.extraction.ExtractionStatus;
import in.devmedi.kiosk.module.document.findings.model.EncounterMetadata;
import in.devmedi.kiosk.module.document.findings.model.InteractionAnalysisStatus;
import in.devmedi.kiosk.module.document.findings.model.LabResult;
import in.devmedi.kiosk.module.document.findings.model.Medication;
import in.devmedi.kiosk.module.document.findings.model.PatientIdentifiers;
import in.devmedi.kiosk.module.document.findings.model.VitalSign;

import java.time.Instant;
import java.util.List;

/**
 * Render-safe, JSON-serialisable view of a document's structured findings
 * together with its underlying extraction state.
 *
 * <p>When no findings could be derived, {@code patient} and {@code encounter}
 * are {@code null} and the collections are always empty (never null).</p>
 *
 * @param documentId        business document id
 * @param extractionStatus  extraction status of the document
 * @param errorCategory     extraction error category ({@link ExtractionErrorCategory#NONE} when irrelevant)
 * @param errorMessage      extraction error explanation, or {@code null}
 * @param patient           decoded patient identifiers, or {@code null}
 * @param encounter         decoded encounter metadata, or {@code null}
 * @param vitals            decoded vitals in document order
 * @param labResults        decoded lab results (with abnormality status) in document order
 * @param medications       decoded medications (with completeness and duplicate flags) in document order
 * @param interactionAnalysisStatus availability of drug-interaction analysis for this extraction
 * @param extractedAt       timestamp of the extraction run
 * @param findingsCreatedAt timestamp findings were computed, or {@code null}
 */
public record StructuredFindingsResponse(String documentId,
                                         ExtractionStatus extractionStatus,
                                         ExtractionErrorCategory errorCategory,
                                         String errorMessage,
                                         PatientIdentifiers patient,
                                         EncounterMetadata encounter,
                                         List<VitalSign> vitals,
                                         List<LabResult> labResults,
                                         List<Medication> medications,
                                         InteractionAnalysisStatus interactionAnalysisStatus,
                                         Instant extractedAt,
                                         Instant findingsCreatedAt) {
}