package in.devmedi.kiosk.module.ocr;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OCR provider selection and boundaries. Switches come only from configuration
 * or the environment; nothing here invents credentials or an engine.
 *
 * @param provider  selected provider name from {@code medikiosk.ocr.provider}
 * @param languages candidate languages to report capabilities for
 * @param timeoutMs default per-provider timeout that a real engine would use
 */
@ConfigurationProperties(prefix = "medikiosk.ocr")
public record OcrProperties(String provider,
                            String[] languages,
                            long timeoutMs) {

    public OcrProperties {
        languages = languages == null ? new String[]{"en", "hi", "te"} : languages.clone();
        if (timeoutMs <= 0) {
            timeoutMs = 15000;
        }
    }

    /** @return the selected provider, defaulting to the honest local-dev seam. */
    public String resolvedProvider() {
        return provider == null || provider.isBlank() ? "local-dev" : provider.trim();
    }
}