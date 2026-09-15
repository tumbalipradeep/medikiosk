package in.devmedi.kiosk.module.document.findings;

import in.devmedi.kiosk.module.document.extraction.ExtractionMethod;
import in.devmedi.kiosk.module.document.extraction.ExtractionStatus;

import java.time.Instant;

/**
 * Enriched document metadata for the physician document workspace, including
 * counts derived deterministically from persisted findings.
 *
 * @param documentId             business document id
 * @param originalFilename       original upload filename
 * @param contentType            upload content type
 * @param fileSize               upload file size in bytes
 * @param uploadedAt             upload timestamp
 * @param extractionStatus       current extraction status
 * @param extractionStatusLabel  human-readable extraction status text
 * @param extractionBadgeClass   Bootstrap badge class for the extraction status
 * @param extractionMethod       proven extraction method, or {@code null}
 * @param extractionProvider     proven extraction provider, or {@code null}
 * @param hasFindings            whether structured findings are available
 * @param labCount               number of decoded lab results
 * @param abnormalLabCount       number of lab results with LOW or HIGH status
 * @param medicationCount        number of decoded medications
 * @param duplicateMedCount      number of medications occurring more than once
 */
public record DocumentWorkspaceItem(String documentId,
                                    String originalFilename,
                                    String contentType,
                                    long fileSize,
                                    Instant uploadedAt,
                                    ExtractionStatus extractionStatus,
                                    String extractionStatusLabel,
                                    String extractionBadgeClass,
                                    ExtractionMethod extractionMethod,
                                    String extractionProvider,
                                    boolean hasFindings,
                                    int labCount,
                                    int abnormalLabCount,
                                    int medicationCount,
                                    int duplicateMedCount) {
}