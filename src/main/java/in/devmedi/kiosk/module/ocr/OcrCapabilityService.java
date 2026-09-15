package in.devmedi.kiosk.module.ocr;

import in.devmedi.kiosk.module.document.extraction.OcrProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Builds the honest capability report for OCR and handwriting recognition.
 *
 * <p>The report lists every bound {@link OcrProvider} engine with exactly one
 * {@link OcrProviderStatus} and a per-language status. The selected provider is
 * resolved from {@code medikiosk.ocr.provider}; when no provider matches, a
 * deterministic fallback is chosen (the single bound engine, else
 * {@link OcrProviderStatus#NOT_IMPLEMENTED}). Nothing here invents engines or
 * credentials.</p>
 */
@Service
public class OcrCapabilityService {

    private static final String NOT_DECLARED = "provider does not declare per-language capability";

    private final List<OcrProvider> engines;
    private final List<HandwritingRecognitionProvider> hwrProviders;
    private final OcrProperties properties;

    public OcrCapabilityService(List<OcrProvider> engines,
                                List<HandwritingRecognitionProvider> hwrProviders,
                                OcrProperties properties) {
        this.engines = engines == null ? List.of() : List.copyOf(engines);
        this.hwrProviders = hwrProviders == null ? List.of() : List.copyOf(hwrProviders);
        this.properties = properties;
    }

    public OcrCapabilitiesResponse ocrCapabilities() {
        List<OcrCapability> capabilities = engines.stream()
                .sorted(Comparator.comparing(OcrProvider::engineName))
                .map(this::toCapability)
                .toList();

        String configuredName = properties.resolvedProvider();
        Optional<OcrProvider> configured =
                engines.stream().filter(e -> matches(e.engineName(), configuredName)).findFirst();

        OcrProvider selected = configured.orElseGet(this::deterministicFallback);
        OcrCapability selectedCapability = selected == null
                ? OcrCapability.notImplemented(configuredName, "No OCR engine is bound for the configured provider")
                : toCapability(selected);

        String fallbackNote = configured.isEmpty() && selected != null
                ? "Configured provider '" + configuredName + "' is not bound; fell back deterministically to '"
                + selected.engineName() + "'."
                : null;

        return new OcrCapabilitiesResponse(configuredName, selectedCapability.status(), capabilities, fallbackNote);
    }

    public HwrCapabilityResponse hwrCapability() {
        HandwritingRecognitionProvider selected = hwrProviders.stream()
                .filter(HandwritingRecognitionProvider::isAvailable)
                .findFirst()
                .orElseGet(() -> hwrProviders.isEmpty() ? null : hwrProviders.get(0));

        if (selected == null) {
            return new HwrCapabilityResponse("none", OcrProviderStatus.NOT_IMPLEMENTED, false,
                    Map.of(), "No handwriting recognition provider is bound in this deployment.");
        }
        return new HwrCapabilityResponse(
                selected.engineName(),
                selected.providerStatus(),
                selected.isAvailable(),
                OcrCapability.onlyIf(selected.supportedLanguages(),
                        OcrProviderStatus.REAL_AND_VERIFIED, OcrProviderStatus.NOT_IMPLEMENTED),
                "Handwriting recognition is not implemented in this deployment; ordinary OCR is never relabelled as HWR.");
    }

    private OcrCapability toCapability(OcrProvider provider) {
        if (provider instanceof OcrProviderStatusSource source) {
            long timeout = source.timeoutMillis();
            return new OcrCapability(
                    provider.engineName(),
                    source.ocrStatus(),
                    provider.isAvailable(),
                    timeout,
                    OcrCapability.onlyIf(source.supportedLanguages(), source.ocrStatus().isOperational()
                                    ? source.ocrStatus() : OcrProviderStatus.NOT_IMPLEMENTED,
                            OcrProviderStatus.NOT_IMPLEMENTED),
                    capabilityNote(source, provider));
        }
        OcrProviderStatus status = provider.isAvailable()
                ? OcrProviderStatus.REAL_AND_VERIFIED : OcrProviderStatus.NOT_IMPLEMENTED;
        return new OcrCapability(
                provider.engineName(),
                status,
                provider.isAvailable(),
                -1,
                OcrCapability.onlyIf(java.util.Set.of(), status, OcrProviderStatus.NOT_IMPLEMENTED),
                NOT_DECLARED);
    }

    private String capabilityNote(OcrProviderStatusSource source, OcrProvider provider) {
        StringBuilder note = new StringBuilder();
        if (source.ocrStatus() == OcrProviderStatus.NOT_IMPLEMENTED) {
            note.append("No OCR engine is bundled or credential-verified in this deployment; image text cannot be extracted.");
        } else if (source.ocrStatus() == OcrProviderStatus.IMPLEMENTED_BUT_NOT_CREDENTIAL_VERIFIED) {
            note.append("Integration is implemented but not credential-verified; treat as not live.");
        } else {
            note.append("Engine reported operational: ").append(provider.engineName());
        }
        return note.toString();
    }

    private OcrProvider deterministicFallback() {
        List<OcrProvider> available = engines.stream().filter(OcrProvider::isAvailable).toList();
        if (!available.isEmpty()) {
            return available.get(0);
        }
        return engines.stream().findFirst().orElse(null);
    }

    private boolean matches(String engineName, String configuredName) {
        if (engineName == null) {
            return false;
        }
        String normalizedEngine = engineName.toLowerCase();
        String normalizedConfigured = configuredName.toLowerCase();
        return normalizedEngine.equals(normalizedConfigured)
                || normalizedEngine.contains(normalizedConfigured);
    }

    /** Honest capability summary for the OCR boundary. */
    public record OcrCapabilitiesResponse(String configuredProvider,
                                          OcrProviderStatus overallStatus,
                                          List<OcrCapability> engines,
                                          String fallbackNote) {
    }

    /** Honest capability summary for the handwriting-recognition boundary. */
    public record HwrCapabilityResponse(String provider,
                                        OcrProviderStatus status,
                                        boolean available,
                                        Map<OcrLanguage, OcrProviderStatus> languageStatus,
                                        String note) {
    }
}