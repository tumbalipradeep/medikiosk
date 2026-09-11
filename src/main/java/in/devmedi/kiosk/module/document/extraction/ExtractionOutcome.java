package in.devmedi.kiosk.module.document.extraction;

import java.util.List;

/**
 * Immutable outcome of a document text-extraction run, independent of any
 * persistence artifact. The service layer turns this into an entity and the
 * {@link ExtractionResult} DTO returned to clients.
 */
public record ExtractionOutcome(ExtractionStatus status,
                                ExtractionErrorCategory errorCategory,
                                String errorMessage,
                                String extractedText,
                                int pageCount,
                                List<ExtractionResult.PageText> pages) {

    public static ExtractionOutcome unsupported(ExtractionErrorCategory category, String message) {
        return new ExtractionOutcome(ExtractionStatus.UNSUPPORTED, category, message, null, 0, List.of());
    }

    public static ExtractionOutcome failed(ExtractionErrorCategory category, String message) {
        return new ExtractionOutcome(ExtractionStatus.FAILED, category, message, null, 0, List.of());
    }

    public static ExtractionOutcome noText(String message, int pageCount, List<ExtractionResult.PageText> pages) {
        return new ExtractionOutcome(ExtractionStatus.NO_TEXT, ExtractionErrorCategory.NONE, message, "", pageCount, pages);
    }
}