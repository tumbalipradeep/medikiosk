package in.devmedi.kiosk.module.document.extraction;

import in.devmedi.kiosk.module.document.entity.ClinicalDocumentExtraction;
import in.devmedi.kiosk.module.document.entity.ClinicalDocumentPageText;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Render-safe extraction result for one clinical document. All fields are plain
 * values, so no JPA entities or lazy associations leak out of the data layer.
 *
 * @param documentId        the business {@code documentId} of the clinical document
 * @param status            overall extraction status
 * @param errorCategory     failure/unsupported category ({@link ExtractionErrorCategory#NONE} when irrelevant)
 * @param errorMessage      human-readable explanation when extraction did not succeed
 * @param extractedText     combined document text (page texts joined in order)
 * @param pageCount         number of pages processed
 * @param pages             page-level text in ascending page-number order
 * @param extractedAt       timestamp of the extraction run
 * @param extractionMethod  how text was extracted (PDF text layer vs OCR), or {@code null}
 * @param providerName      engine that produced the extraction, or {@code null}
 */
public record ExtractionResult(String documentId,
                               ExtractionStatus status,
                               ExtractionErrorCategory errorCategory,
                               String errorMessage,
                               String extractedText,
                               int pageCount,
                               List<PageText> pages,
                               Instant extractedAt,
                               ExtractionMethod extractionMethod,
                               String providerName) {

    public record PageText(int pageNumber, String text) {
    }

    public static ExtractionResult from(ClinicalDocumentExtraction entity, String documentId) {
        List<PageText> pages = entity.getPages().stream()
                .sorted(Comparator.comparingInt(ClinicalDocumentPageText::getPageNumber))
                .map(page -> new PageText(page.getPageNumber(), page.getPageText()))
                .toList();
        return new ExtractionResult(
                documentId,
                entity.getStatus(),
                entity.getErrorCategory(),
                entity.getErrorMessage(),
                entity.getExtractedText(),
                entity.getPageCount(),
                pages,
                entity.getExtractedAt(),
                entity.getExtractionMethod(),
                entity.getProviderName());
    }
}