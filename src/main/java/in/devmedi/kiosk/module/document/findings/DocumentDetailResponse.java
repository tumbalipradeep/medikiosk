package in.devmedi.kiosk.module.document.findings;

import in.devmedi.kiosk.module.document.extraction.ExtractionErrorCategory;
import in.devmedi.kiosk.module.document.extraction.ExtractionMethod;
import in.devmedi.kiosk.module.document.extraction.ExtractionStatus;
import in.devmedi.kiosk.module.document.findings.model.EncounterMetadata;
import in.devmedi.kiosk.module.document.findings.model.InteractionAnalysisStatus;
import in.devmedi.kiosk.module.document.findings.model.LabResult;
import in.devmedi.kiosk.module.document.findings.model.Medication;
import in.devmedi.kiosk.module.document.findings.model.PatientIdentifiers;
import in.devmedi.kiosk.module.document.findings.model.VitalSign;
import in.devmedi.kiosk.module.document.extraction.ExtractionResult;

import java.time.Instant;
import java.util.List;

/**
 * Complete physician-facing document detail view, combining document metadata,
 * extraction results, and structured findings in a single DTO.
 *
 * <p>When no findings could be derived, the findings fields are {@code null} or
 * empty. Page-level extracted text is only populated for EXTRACTED documents.</p>
 *
 * @param documentId              business document id
 * @param originalFilename        original upload filename
 * @param contentType             upload content type
 * @param fileSize                upload file size in bytes
 * @param uploadedAt              upload timestamp
 * @param extractionStatus        extraction status
 * @param extractionStatusLabel   human-readable extraction status
 * @param extractionBadgeClass    Bootstrap badge class
 * @param extractionErrorCategory extraction error category
 * @param extractionErrorMessage  extraction error explanation
 * @param extractedAt             extraction timestamp
 * @param extractionMethod        how text was extracted (PDF text layer vs OCR), or {@code null}
 * @param extractionProvider      engine that produced the extraction, or {@code null}
 * @param sourceLanguage          language of the extracted source text, or {@code null} when the extraction pre-dates language provenance (V24) or none was recorded
 * @param pageCount               page count
 * @param pages                   page-level extracted text
 * @param patient                 decoded patient identifiers, or {@code null}
 * @param encounter               decoded encounter metadata, or {@code null}
 * @param vitals                  decoded vitals in document order
 * @param labResults              decoded lab results with abnormality status
 * @param medications             decoded medications with completeness flags
 * @param interactionAnalysisStatus interaction analysis availability
 * @param findingsCreatedAt       when findings were computed, or {@code null}
 */
public record DocumentDetailResponse(String documentId,
                                     String originalFilename,
                                     String contentType,
                                     long fileSize,
                                     Instant uploadedAt,
                                     ExtractionStatus extractionStatus,
                                     String extractionStatusLabel,
                                     String extractionBadgeClass,
                                     ExtractionErrorCategory extractionErrorCategory,
                                     String extractionErrorMessage,
                                     Instant extractedAt,
                                     ExtractionMethod extractionMethod,
                                     String extractionProvider,
                                     String sourceLanguage,
                                     int pageCount,
                                     List<ExtractionResult.PageText> pages,
                                     PatientIdentifiers patient,
                                     EncounterMetadata encounter,
                                     List<VitalSign> vitals,
                                     List<LabResult> labResults,
                                     List<Medication> medications,
                                     InteractionAnalysisStatus interactionAnalysisStatus,
                                     Instant findingsCreatedAt) {
}