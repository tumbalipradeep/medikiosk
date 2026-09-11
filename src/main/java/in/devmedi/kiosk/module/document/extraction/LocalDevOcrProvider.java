package in.devmedi.kiosk.module.document.extraction;

import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;

/**
 * Deterministic local development implementation of {@link OcrProvider}.
 *
 * <p>No OCR engine (e.g. Tesseract) is bundled in this deployment and no
 * external credential-based service is used, so image text extraction is not
 * possible here. {@link #isAvailable()} therefore always returns {@code false}
 * and {@link #extract(BufferedImage)} returns a truthful unsupported outcome.
 * Swapping in a real engine keeps this contract: callers check
 * {@link #isAvailable()} before requesting OCR and never receive fabricated
 * clinical text.</p>
 */
@Component
public class LocalDevOcrProvider implements OcrProvider {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public String engineName() {
        return "local-dev (no OCR engine)";
    }

    @Override
    public ExtractionOutcome extract(BufferedImage image) {
        return ExtractionOutcome.unsupported(ExtractionErrorCategory.NO_OCR_ENGINE,
                "OCR engine is not available in this deployment; image text cannot be extracted");
    }
}