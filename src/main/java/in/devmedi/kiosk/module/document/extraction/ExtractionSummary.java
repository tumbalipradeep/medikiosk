package in.devmedi.kiosk.module.document.extraction;

import in.devmedi.kiosk.module.document.entity.ClinicalDocumentExtraction;

import java.time.Instant;

/**
 * Lightweight, text-free summary of a document's extraction state, intended for
 * list/review rendering where the full extracted body is not needed.
 */
public record ExtractionSummary(String documentId,
                                ExtractionStatus status,
                                ExtractionErrorCategory errorCategory,
                                String errorMessage,
                                int pageCount,
                                boolean hasExtractableText,
                                Instant extractedAt) {

    public static ExtractionSummary pending(String documentId) {
        return new ExtractionSummary(documentId, ExtractionStatus.PENDING, ExtractionErrorCategory.NONE,
                null, 0, false, null);
    }

    public static ExtractionSummary from(ClinicalDocumentExtraction extraction) {
        boolean hasText = extraction.getStatus() == ExtractionStatus.EXTRACTED
                && extraction.getExtractedText() != null
                && !extraction.getExtractedText().isBlank();
        return new ExtractionSummary(
                extraction.getDocument().getDocumentId(),
                extraction.getStatus(),
                extraction.getErrorCategory(),
                extraction.getErrorMessage(),
                extraction.getPageCount(),
                hasText,
                extraction.getExtractedAt());
    }

    public String statusLabel() {
        return switch (status) {
            case PENDING -> "Not extracted";
            case EXTRACTED -> "Text extracted";
            case NO_TEXT -> "No extractable text";
            case UNSUPPORTED -> "Cannot extract";
            case FAILED -> "Extraction failed";
        };
    }

    public String badgeClass() {
        return switch (status) {
            case PENDING -> "text-bg-light border text-muted";
            case EXTRACTED -> "text-bg-success";
            case NO_TEXT -> "text-bg-warning text-dark";
            case UNSUPPORTED -> "text-bg-secondary";
            case FAILED -> "text-bg-danger";
        };
    }
}