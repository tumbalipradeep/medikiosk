package in.devmedi.kiosk.module.document.extraction;

import java.awt.image.BufferedImage;

/**
 * Abstraction over OCR engines used to extract text from image documents
 * (JPEG/PNG). Implementations must be truthful: if no real OCR engine is
 * available or configured, {@link #isAvailable()} returns {@code false} and
 * {@link #extract(BufferedImage)} reports an unsupported outcome rather than
 * fabricating document text.
 */
public interface OcrProvider {

    /**
     * @return {@code true} only when a real OCR engine is configured and usable.
     */
    boolean isAvailable();

    /**
     * @return a human-readable identifier of the configured engine, e.g. {@code "none"}.
     */
    String engineName();

    /**
     * Runs OCR over the given image.
     *
     * @param image decoded image bytes
     * @return extraction outcome; never {@code null}
     */
    ExtractionOutcome extract(BufferedImage image);
}