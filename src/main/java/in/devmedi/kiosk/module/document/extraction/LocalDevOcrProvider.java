package in.devmedi.kiosk.module.document.extraction;

import in.devmedi.kiosk.module.ocr.OcrLanguage;
import in.devmedi.kiosk.module.ocr.OcrProviderStatus;
import in.devmedi.kiosk.module.ocr.OcrProviderStatusSource;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.util.Set;

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
public class LocalDevOcrProvider implements OcrProvider, OcrProviderStatusSource {

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

    @Override
    public OcrProviderStatus ocrStatus() {
        return OcrProviderStatus.NOT_IMPLEMENTED;
    }

    @Override
    public long timeoutMillis() {
        return -1;
    }

    @Override
    public Set<OcrLanguage> supportedLanguages() {
        return Set.of();
    }
}