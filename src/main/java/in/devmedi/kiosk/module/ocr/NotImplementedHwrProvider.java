package in.devmedi.kiosk.module.ocr;

import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Honest base implementation of the HWR boundary: handwriting recognition is
 * not implemented in this deployment, so this provider reports
 * {@link OcrProviderStatus#NOT_IMPLEMENTED} and never fabricates a transcript.
 */
@Component
public class NotImplementedHwrProvider implements HandwritingRecognitionProvider {

    public static final String ENGINE_NAME = "none";

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public String engineName() {
        return ENGINE_NAME;
    }

    @Override
    public OcrProviderStatus providerStatus() {
        return OcrProviderStatus.NOT_IMPLEMENTED;
    }

    @Override
    public Set<OcrLanguage> supportedLanguages() {
        return Set.of();
    }

    @Override
    public HandwritingRecognition recognize() {
        return HandwritingRecognition.notImplemented(
                "Handwriting recognition is not implemented in this deployment; ordinary OCR output is never relabelled as HWR.");
    }
}