package in.devmedi.kiosk.module.document.extraction;

import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Routes a securely stored document binary to the right text-extraction path
 * (PDF via PDFBox, images via an {@link OcrProvider}) and produces an
 * {@link ExtractionOutcome}. Never mutates the source file.
 */
@Component
public class DocumentTextProcessor {

    private final PdfTextExtractor pdfTextExtractor;
    private final OcrProvider ocrProvider;

    public DocumentTextProcessor(PdfTextExtractor pdfTextExtractor, OcrProvider ocrProvider) {
        this.pdfTextExtractor = pdfTextExtractor;
        this.ocrProvider = ocrProvider;
    }

    /**
     * @return whether an OCR engine is available for image documents in this
     *         deployment.
     */
    public boolean imageOcrAvailable() {
        return ocrProvider.isAvailable();
    }

    /**
     * @return the honest engine identifier that would process image documents,
     *         even when unavailable (provenance should record what was missing).
     */
    public String imageOcrEngineName() {
        return ocrProvider.engineName();
    }

    /**
     * @return the extraction method this processor would apply for the given
     *         content type (PDF text layer, OCR image, or none).
     */
    public static ExtractionMethod methodFor(String contentType) {
        if (contentType == null) {
            return ExtractionMethod.NONE;
        }
        return switch (contentType.toLowerCase()) {
            case "application/pdf" -> ExtractionMethod.PDF_TEXT;
            case "image/jpeg", "image/png" -> ExtractionMethod.OCR_IMAGE;
            default -> ExtractionMethod.NONE;
        };
    }

    public ExtractionOutcome process(Path sourceFile, String contentType) throws IOException {
        String type = contentType == null ? "" : contentType.toLowerCase();
        return switch (type) {
            case "application/pdf" -> pdfTextExtractor.toOutcome(pdfTextExtractor.extract(sourceFile));
            case "image/jpeg", "image/png" -> processImage(sourceFile);
            default -> ExtractionOutcome.unsupported(ExtractionErrorCategory.UNSUPPORTED_TYPE,
                    "Document type is not supported for text extraction: " + contentType);
        };
    }

    private ExtractionOutcome processImage(Path sourceFile) throws IOException {
        if (!ocrProvider.isAvailable()) {
            return ExtractionOutcome.unsupported(ExtractionErrorCategory.NO_OCR_ENGINE,
                    "OCR engine is not available in this deployment; image text cannot be extracted");
        }
        BufferedImage image = ImageIO.read(sourceFile.toFile());
        if (image == null) {
            return ExtractionOutcome.failed(ExtractionErrorCategory.MALFORMED,
                    "The image could not be decoded");
        }
        return ocrProvider.extract(image);
    }
}