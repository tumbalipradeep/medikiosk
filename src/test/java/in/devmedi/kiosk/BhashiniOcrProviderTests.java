package in.devmedi.kiosk.module.document.extraction;

import in.devmedi.kiosk.module.ocr.OcrLanguage;
import in.devmedi.kiosk.module.ocr.OcrProperties;
import in.devmedi.kiosk.module.ocr.OcrProviderStatus;
import in.devmedi.kiosk.module.voice.config.VoiceProperties;
import in.devmedi.kiosk.module.voice.provider.BhashiniException;
import in.devmedi.kiosk.module.voice.provider.BhashiniFailure;
import in.devmedi.kiosk.module.voice.provider.BhashiniGateway;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.springframework.web.client.RestClient;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the Bhashini printed-text OCR provider's honesty rules.
 *
 * <p>The provider is a REAL integration against the Bhashini/ULCA pipeline,
 * but this deployment has no verified credentials, so its public status must
 * be {@code IMPLEMENTED_BUT_NOT_CREDENTIAL_VERIFIED} — never presented as
 * live. Without credentials it must refuse extraction (no fabricated text),
 * and provider failures must map to failed/unsupported outcomes, never to
 * invented clinical text. Handwriting recognition stays a separate,
 * unavailable capability.</p>
 */
class BhashiniOcrProviderTests {

    private static VoiceProperties.Bhashini settings(String userId, String apiKey, String pipelineId, String apiUrl) {
        VoiceProperties.Bhashini settings = new VoiceProperties.Bhashini();
        settings.setUserId(userId);
        settings.setApiKey(apiKey);
        settings.setPipelineId(pipelineId);
        settings.setApiUrl(apiUrl);
        return settings;
    }

    private static BhashiniOcrProvider provider(VoiceProperties.Bhashini settings) {
        return new BhashiniOcrProvider(
                voiceProperties(settings),
                new OcrProperties("bhashini", new String[]{"en", "hi", "te"}, 15000),
                RestClient.builder());
    }

    private static VoiceProperties voiceProperties(VoiceProperties.Bhashini settings) {
        VoiceProperties properties = new VoiceProperties();
        properties.setBhashini(settings);
        return properties;
    }

    @Test
    void statusIsConservativelyImplementedButNotCredentialVerifiedEvenWithCredentials() {
        BhashiniOcrProvider provider = provider(settings("u", "k", "p", "https://api.example"));
        // Even complete credentials do not promote the engine to REAL_AND_VERIFIED:
        // only an operator-verified deployment may claim that.
        assertThat(provider.ocrStatus())
                .isEqualTo(OcrProviderStatus.IMPLEMENTED_BUT_NOT_CREDENTIAL_VERIFIED);
        assertThat(provider.ocrStatus().isOperational()).isFalse();
    }

    @Test
    void withoutCredentialsTheProviderIsUnavailableAndRefusesExtraction() {
        BhashiniOcrProvider provider = provider(settings("", "", "", ""));
        assertThat(provider.isAvailable()).isFalse();
        assertThat(provider.supportedLanguages()).isEmpty();

        BufferedImage image = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
        ExtractionOutcome outcome = provider.extract(image);

        assertThat(outcome.status()).isEqualTo(ExtractionStatus.UNSUPPORTED);
        assertThat(outcome.errorCategory()).isEqualTo(ExtractionErrorCategory.NO_OCR_ENGINE);
        assertThat(outcome.extractedText()).isNull();
    }

    @Test
    void recognizedTextIsExtractedWithPageProvenance() {
        BufferedImage image = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);

        // The provider builds its gateway in its constructor, so the provider
        // itself must be constructed INSIDE the mocked scope.
        try (MockedConstruction<BhashiniGateway> mocked = mockConstruction(BhashiniGateway.class,
                (gateway, context) -> when(gateway.recognizePrintedText(anyString(), any()))
                        .thenReturn("Hemoglobin: 13.5 g/dL"))) {
            BhashiniOcrProvider provider = provider(settings("u", "k", "p", "https://api.example"));

            ExtractionOutcome outcome = provider.extract(image);
            assertThat(outcome.status()).isEqualTo(ExtractionStatus.EXTRACTED);
            assertThat(outcome.extractedText()).isEqualTo("Hemoglobin: 13.5 g/dL");
            assertThat(outcome.pages()).hasSize(1);
            assertThat(outcome.pages().get(0).pageNumber()).isEqualTo(1);
            assertThat(outcome.pages().get(0).text()).contains("Hemoglobin");
        }
    }

    @Test
    void providerFailuresNeverFabricateClinicalText() {
        BufferedImage image = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);

        try (MockedConstruction<BhashiniGateway> mocked = mockConstruction(BhashiniGateway.class,
                (gateway, context) -> when(gateway.recognizePrintedText(anyString(), any()))
                        .thenThrow(new BhashiniException(BhashiniFailure.TIMEOUT, "no answer")))) {
            BhashiniOcrProvider provider = provider(settings("u", "k", "p", "https://api.example"));

            ExtractionOutcome outcome = provider.extract(image);
            assertThat(outcome.status()).isEqualTo(ExtractionStatus.FAILED);
            assertThat(outcome.extractedText()).isNull();
        }
    }

    @Test
    void anEmptyOcrResultIsReportedAsNoTextNotAsSuccess() {
        BufferedImage image = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);

        try (MockedConstruction<BhashiniGateway> mocked = mockConstruction(BhashiniGateway.class,
                (gateway, context) -> when(gateway.recognizePrintedText(anyString(), any()))
                        .thenReturn("   "))) {
            BhashiniOcrProvider provider = provider(settings("u", "k", "p", "https://api.example"));

            ExtractionOutcome outcome = provider.extract(image);
            assertThat(outcome.status()).isEqualTo(ExtractionStatus.NO_TEXT);
            assertThat(outcome.extractedText()).isEmpty();
        }
    }

    @Test
    void languageCapabilityIsOnlyClaimedWhenCredentialsExist() {
        BhashiniOcrProvider without = provider(settings("", "", "", ""));
        assertThat(without.supportedLanguages()).isEmpty();

        BhashiniOcrProvider with = provider(settings("u", "k", "p", "https://api.example"));
        Set<OcrLanguage> languages = with.supportedLanguages();
        assertThat(languages).contains(OcrLanguage.ENGLISH, OcrLanguage.HINDI, OcrLanguage.TELUGU);
    }
}
