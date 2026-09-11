package in.devmedi.kiosk.module.voice.speech;

import in.devmedi.kiosk.module.voice.language.LanguageService;
import in.devmedi.kiosk.module.voice.language.SupportedLanguage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the speech-recognition abstraction contract and the deterministic
 * fallback used when no real ASR provider is configured.
 */
class SpeechRecognitionServiceTests {

    private final SpeechRecognitionService fallback =
            new UnavailableSpeechRecognitionService(new LanguageService());

    @Test
    void transcribedResultCarriesTheTranscriptAndLanguage() {
        SpeechRecognitionService stub = request -> SpeechRecognitionResult.transcribed("chest pain", "en-IN");

        SpeechRecognitionResult result = stub.transcribe(new SpeechRecognitionRequest(new byte[]{1, 2}, "en-IN", "s-1"));

        assertThat(result.status()).isEqualTo(SpeechRecognitionStatus.TRANSCRIBED);
        assertThat(result.transcript()).isEqualTo("chest pain");
        assertThat(result.language()).isEqualTo("en-IN");
    }

    @Test
    void emptyResultReportsEmptyStatusWithBlankTranscript() {
        SpeechRecognitionService stub = request -> SpeechRecognitionResult.empty("hi-IN");

        SpeechRecognitionResult result = stub.transcribe(new SpeechRecognitionRequest(new byte[0], "hi-IN", "s-1"));

        assertThat(result.status()).isEqualTo(SpeechRecognitionStatus.EMPTY);
        assertThat(result.transcript()).isBlank();
    }

    @Test
    void fallbackReportsUnavailableWithoutAnyTranscript() {
        SpeechRecognitionResult result =
                fallback.transcribe(new SpeechRecognitionRequest(new byte[0], "en-IN", "s-1"));

        assertThat(result.status()).isEqualTo(SpeechRecognitionStatus.UNAVAILABLE);
        assertThat(result.transcript()).isNullOrEmpty();
    }

    @Test
    void fallbackReportsUnsupportedForLanguagesOutsideTheSupportedSet() {
        SpeechRecognitionResult result =
                fallback.transcribe(new SpeechRecognitionRequest(new byte[0], "fr-FR", "s-1"));

        assertThat(result.status()).isEqualTo(SpeechRecognitionStatus.UNSUPPORTED);
        assertThat(result.transcript()).isNullOrEmpty();
    }

    @Test
    void failedResultNeverCarriesAnInventedTranscript() {
        SpeechRecognitionService stub = request -> SpeechRecognitionResult.failed("en-IN", 42L);

        SpeechRecognitionResult result = stub.transcribe(new SpeechRecognitionRequest(new byte[0], "en-IN", "s-1"));

        assertThat(result.status()).isEqualTo(SpeechRecognitionStatus.FAILED);
        assertThat(result.transcript()).isNullOrEmpty();
    }

    @Test
    void fallbackNeverInventsSpeechForAnySupportedLanguage() {
        for (SupportedLanguage language : SupportedLanguage.values()) {
            SpeechRecognitionResult result =
                    fallback.transcribe(new SpeechRecognitionRequest(new byte[0], language.code(), "s-1"));
            assertThat(result.status()).isNotEqualTo(SpeechRecognitionStatus.TRANSCRIBED);
            assertThat(result.transcript()).isNullOrEmpty();
        }
    }

    @Test
    void audioIsNeverReadOrLoggedByTheContract() {
        SpeechRecognitionResult result = fallback.transcribe(
                new SpeechRecognitionRequest(new byte[]{0x00, 0x01, 0x02}, "ta-IN", "s-1"));
        assertThat(result.status()).isEqualTo(SpeechRecognitionStatus.UNAVAILABLE);
    }
}