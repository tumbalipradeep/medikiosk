package in.devmedi.kiosk.module.ocr;

import java.util.Set;

/**
 * Optional capability contract for an {@code OcrProvider} implementation so the
 * capability registry can report an honest per-provider status instead of
 * guessing from {@code isAvailable()}.
 */
public interface OcrProviderStatusSource {

    /**
     * @return exactly one {@link OcrProviderStatus} for this provider.
     */
    OcrProviderStatus ocrStatus();

    /**
     * @return provider HTTP/rendering timeout in milliseconds, or {@code -1} if
     *         the provider is purely local/deterministic.
     */
    long timeoutMillis();

    /**
     * @return set of languages this provider genuinely supports when operational.
     */
    Set<OcrLanguage> supportedLanguages();
}