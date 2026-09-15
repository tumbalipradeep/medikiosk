package in.devmedi.kiosk.module.ocr;

import java.util.Set;

/**
 * Boundary for handwritten-document recognition (HWR), kept strictly separate
 * from ordinary OCR. Ordinary OCR must never be labelled as handwriting
 * recognition.
 */
public interface HandwritingRecognitionProvider {

    /**
     * @return {@code true} only when genuine handwriting recognition is
     *         configured and usable in this deployment.
     */
    boolean isAvailable();

    /**
     * @return human-readable engine identifier, e.g. {@code "none"}.
     */
    String engineName();

    /**
     * @return exactly one {@link OcrProviderStatus}.
     */
    OcrProviderStatus providerStatus();

    /**
     * @param language languages for which support is reported.
     * @return honestly-lowered capability map for this provider.
     */
    Set<OcrLanguage> supportedLanguages();

    /**
     * Attempts handwriting recognition. Implementations must never fabricate a
     * transcript.
     *
     * @return immutable {@link HandwritingRecognition}; never {@code null}.
     */
    HandwritingRecognition recognize();
}