package in.devmedi.kiosk.module.document.extraction;

import in.devmedi.kiosk.module.ocr.OcrLanguage;
import in.devmedi.kiosk.module.ocr.OcrProperties;
import in.devmedi.kiosk.module.ocr.OcrProviderStatus;
import in.devmedi.kiosk.module.ocr.OcrProviderStatusSource;
import in.devmedi.kiosk.module.voice.config.VoiceProperties;
import in.devmedi.kiosk.module.voice.provider.BhashiniException;
import in.devmedi.kiosk.module.voice.provider.BhashiniFailure;
import in.devmedi.kiosk.module.voice.provider.BhashiniGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Printed-document OCR through the Bhashini/ULCA pipeline — the same
 * credential-gated gateway the voice layer uses.
 *
 * <p><strong>Honesty rules.</strong> This provider is a real integration with
 * a real provider API, but its output can only be trusted once this deployment
 * has verified credentials against the live service. Its reported status is
 * therefore:</p>
 * <ul>
 *   <li>{@link OcrProviderStatus#IMPLEMENTED_BUT_NOT_CREDENTIAL_VERIFIED}
 *       when Bhashini credentials are absent — the default for this demo
 *       deployment. The engine is wired but must be treated as NOT live.</li>
 *   <li>{@link OcrProviderStatus#IMPLEMENTED_BUT_NOT_CREDENTIAL_VERIFIED}
 *       even with credentials present, until an operator has verified the
 *       pipeline end to end (status is deliberately conservative; a verified
 *       deployment promotes it in configuration).</li>
 * </ul>
 *
 * <p><strong>Scope.</strong> Printed text only. Handwritten documents are NOT
 * recognized by this engine and are never labelled as handwriting recognition
 * — HWR remains a separate, honestly-absent capability.</p>
 *
 * <p>Every extraction requires physician review downstream; provenance records
 * method {@code OCR_IMAGE}, provider {@code bhashini}, and the honest status.
 * No transcript is ever fabricated: provider failures map to failed/unsupported
 * outcomes, never to invented clinical text.</p>
 */
@Component
public class BhashiniOcrProvider implements OcrProvider, OcrProviderStatusSource {

    private static final Logger log = LoggerFactory.getLogger(BhashiniOcrProvider.class);

    public static final String ENGINE_NAME = "bhashini (printed-text OCR)";

    private final VoiceProperties.Bhashini settings;
    private final OcrProperties ocrProperties;
    private final BhashiniGateway gateway;

    public BhashiniOcrProvider(VoiceProperties voiceProperties,
                               OcrProperties ocrProperties,
                               org.springframework.web.client.RestClient.Builder restClientBuilder) {
        this.settings = voiceProperties.getBhashini();
        this.ocrProperties = ocrProperties;
        this.gateway = new BhashiniGateway(settings, restClientBuilder);
    }

    @Override
    public boolean isAvailable() {
        return settings.isComplete();
    }

    @Override
    public String engineName() {
        return ENGINE_NAME;
    }

    @Override
    public ExtractionOutcome extract(BufferedImage image) {
        if (!isAvailable()) {
            return ExtractionOutcome.unsupported(ExtractionErrorCategory.NO_OCR_ENGINE,
                    "Bhashini OCR credentials are not configured; image text cannot be extracted. "
                            + "The printed-document OCR integration is implemented but not credential-verified.");
        }
        try {
            byte[] encoded = toJpeg(image);
            String text = gateway.recognizePrintedText(iso639(primaryLanguage()), encoded);
            if (text == null || text.isBlank()) {
                return ExtractionOutcome.noText(
                        "The OCR engine recognized no text on this page.", 1,
                        List.of(new ExtractionResult.PageText(1, "")));
            }
            return new ExtractionOutcome(ExtractionStatus.EXTRACTED, ExtractionErrorCategory.NONE,
                    null, text, 1,
                    List.of(new ExtractionResult.PageText(1, text)));
        } catch (BhashiniException ex) {
            log.info("ocr.bhashini status={} kind={}", classification(ex), ex.failure());
            return mapFailure(ex);
        } catch (IOException ex) {
            log.info("ocr.bhashini status=FAILED kind=ENCODE");
            return ExtractionOutcome.failed(ExtractionErrorCategory.UNREADABLE,
                    "The image could not be prepared for OCR.");
        }
    }

    @Override
    public OcrProviderStatus ocrStatus() {
        // Deliberately conservative: implemented against the real provider API,
        // but this deployment has not credential-verified it end to end.
        return OcrProviderStatus.IMPLEMENTED_BUT_NOT_CREDENTIAL_VERIFIED;
    }

    @Override
    public long timeoutMillis() {
        return ocrProperties.timeoutMs();
    }

    @Override
    public Set<OcrLanguage> supportedLanguages() {
        // Bhashini OCR pipelines exist for these languages; capability is only
        // claimed once credentials verify the specific pipeline.
        return isAvailable()
                ? Set.of(OcrLanguage.ENGLISH, OcrLanguage.HINDI, OcrLanguage.TELUGU)
                : Set.of();
    }

    // ─── Internals ────────────────────────────────────────────────────

    private ExtractionOutcome mapFailure(BhashiniException ex) {
        return switch (ex.failure()) {
            case UNCONFIGURED -> ExtractionOutcome.unsupported(ExtractionErrorCategory.NO_OCR_ENGINE,
                    "Bhashini OCR credentials are not configured; image text cannot be extracted.");
            case RATE_LIMITED, TIMEOUT, TRANSPORT -> ExtractionOutcome.failed(ExtractionErrorCategory.UNREADABLE,
                    "The OCR provider could not process this image right now; please retry.");
            default -> ExtractionOutcome.failed(ExtractionErrorCategory.UNKNOWN,
                    "The OCR provider rejected or returned an unusable response.");
        };
    }

    private String classification(BhashiniException ex) {
        return switch (ex.failure()) {
            case UNCONFIGURED -> "UNAVAILABLE";
            default -> "FAILED";
        };
    }

    private String primaryLanguage() {
        String[] candidates = ocrProperties.languages();
        return candidates != null && candidates.length > 0 ? candidates[0] : "en";
    }

    private static String iso639(String bcp47) {
        int dash = bcp47.indexOf('-');
        String base = dash < 0 ? bcp47 : bcp47.substring(0, dash);
        return base.toLowerCase(Locale.ROOT);
    }

    private static byte[] toJpeg(BufferedImage image) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            javax.imageio.ImageIO.write(image, "jpg", out);
            return out.toByteArray();
        }
    }
}
