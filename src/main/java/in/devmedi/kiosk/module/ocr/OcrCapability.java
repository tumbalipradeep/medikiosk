package in.devmedi.kiosk.module.ocr;

import java.util.Map;
import java.util.Set;

/**
 * Honest capability description of one OCR or HWR provider in this deployment.
 *
 * @param engineName       human-readable provider identifier
 * @param status           exactly one {@link OcrProviderStatus}
 * @param available        whether {@code extract(...)} can be called today
 * @param timeoutMillis    configured per-provider timeout, or {@code -1} when none
 * @param languageStatus   per-language honesty, never {@code null}
 * @param note             plain-language provenance note, or {@code null}
 */
public record OcrCapability(String engineName,
                            OcrProviderStatus status,
                            boolean available,
                            long timeoutMillis,
                            Map<OcrLanguage, OcrProviderStatus> languageStatus,
                            String note) {

    public OcrCapability {
        languageStatus = languageStatus == null ? Map.of() : Map.copyOf(languageStatus);
    }

    public OcrCapability withStatus(OcrProviderStatus newStatus) {
        return new OcrCapability(engineName, newStatus, available, timeoutMillis, languageStatus, note);
    }

    public static OcrCapability notImplemented(String engineName, String note) {
        return new OcrCapability(engineName, OcrProviderStatus.NOT_IMPLEMENTED, false, -1,
                allLanguages(OcrProviderStatus.NOT_IMPLEMENTED), note);
    }

    public static Map<OcrLanguage, OcrProviderStatus> allLanguages(OcrProviderStatus status) {
        return Map.of(OcrLanguage.ENGLISH, status, OcrLanguage.HINDI, status, OcrLanguage.TELUGU, status);
    }

    public static Map<OcrLanguage, OcrProviderStatus> onlyIf(Set<OcrLanguage> supported,
                                                             OcrProviderStatus supportedStatus,
                                                             OcrProviderStatus otherwise) {
        Map<OcrLanguage, OcrProviderStatus> map = new java.util.EnumMap<>(OcrLanguage.class);
        for (OcrLanguage language : OcrLanguage.values()) {
            map.put(language, supported.contains(language) ? supportedStatus : otherwise);
        }
        return map;
    }
}