package in.devmedi.kiosk.module.document.extraction;

import in.devmedi.kiosk.module.ocr.OcrProperties;

import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Routes a securely stored document binary to the right text-extraction path
 * (PDF via PDFBox, images via an {@link OcrProvider}) and produces an
 * {@link ExtractionOutcome}. Never mutates the source file.
 *
 * <p>When several OCR engines are bound, the configured provider
 * ({@code medikiosk.ocr.provider}) wins; if it cannot run, an available
 * engine is chosen deterministically. A configured-but-unavailable engine is
 * deliberately NOT silently swapped: extraction then fails honestly with the
 * reason the configured engine could not run, so provenance never hides a
 * degraded deployment. (The capability report shows the fallback note; here
 * clinical evidence must never come from an engine the operator did not
 * select when a selection exists.)</p>
 */
@Component
public class DocumentTextProcessor {

    private final PdfTextExtractor pdfTextExtractor;
    private final OcrProvider configuredOcrProvider;

    public DocumentTextProcessor(PdfTextExtractor pdfTextExtractor,
                                 List<OcrProvider> ocrEngines,
                                 OcrProperties ocrProperties) {
        this.pdfTextExtractor = pdfTextExtractor;
        this.configuredOcrProvider = resolve(ocrEngines, ocrProperties.resolvedProvider());
    }

    /** Same deterministic resolution rule as the capability report. */
    private static OcrProvider resolve(List<OcrProvider> engines, String configuredName) {
        if (engines == null || engines.isEmpty()) {
            throw new IllegalStateException("No OcrProvider is bound; image extraction cannot work");
        }
        return engines.stream()
                .filter(e -> matches(e.engineName(), configuredName))
                .findFirst()
                .orElseGet(() -> engines.stream()
                        .filter(OcrProvider::isAvailable)
                        .findFirst()
                        .orElse(engines.get(0)));
    }

    private static boolean matches(String engineName, String configuredName) {
        if (engineName == null) {
            return false;
        }
        String engine = engineName.toLowerCase(Locale.ROOT);
        String configured = configuredName.toLowerCase(Locale.ROOT);
        return engine.equals(configured) || engine.contains(configured);
    }

    /**
     * @return whether an OCR engine is available for image documents in this
     *         deployment.
     */
    public boolean imageOcrAvailable() {
        return configuredOcrProvider.isAvailable();
    }

    /**
     * @return the honest engine identifier that would process image documents,
     *         even when unavailable (provenance should record what was missing).
     */
    public String imageOcrEngineName() {
        return configuredOcrProvider.engineName();
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
        if (!configuredOcrProvider.isAvailable()) {
            return ExtractionOutcome.unsupported(ExtractionErrorCategory.NO_OCR_ENGINE,
                    "OCR engine '" + configuredOcrProvider.engineName()
                            + "' is not available in this deployment; image text cannot be extracted");
        }
        BufferedImage image = ImageIO.read(sourceFile.toFile());
        if (image == null) {
            return ExtractionOutcome.failed(ExtractionErrorCategory.MALFORMED,
                    "The image could not be decoded");
        }
        return configuredOcrProvider.extract(image);
    }
}