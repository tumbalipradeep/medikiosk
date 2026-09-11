package in.devmedi.kiosk.module.voice.speech;

import in.devmedi.kiosk.module.voice.language.LanguageService;
import in.devmedi.kiosk.module.voice.language.SupportedLanguage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the text-to-speech abstraction contract and the deterministic
 * fallback used when no real TTS provider is configured.
 */
class SpeechSynthesisServiceTests {

    private final SpeechSynthesisService fallback =
            new UnavailableSpeechSynthesisService(new LanguageService());

    @Test
    void synthesizedResultCarriesAudioAndLanguage() {
        SpeechSynthesisService stub = request ->
                SpeechSynthesisResult.synthesized(new byte[]{1, 2, 3, 4}, "en-IN");

        SpeechSynthesisResult result = stub.synthesize(new SpeechSynthesisRequest("Hello", "en-IN", "s-1"));

        assertThat(result.status()).isEqualTo(SpeechSynthesisStatus.SYNTHESIZED);
        assertThat(result.audio()).isNotEmpty();
        assertThat(result.language()).isEqualTo("en-IN");
    }

    @Test
    void fallbackReportsUnavailableWithoutAudio() {
        SpeechSynthesisResult result =
                fallback.synthesize(new SpeechSynthesisRequest("Hello", "en-IN", "s-1"));

        assertThat(result.status()).isEqualTo(SpeechSynthesisStatus.UNAVAILABLE);
        assertThat(result.audio()).isEmpty();
    }

    @Test
    void fallbackReportsUnsupportedForLanguagesOutsideTheSupportedSet() {
        SpeechSynthesisResult result =
                fallback.synthesize(new SpeechSynthesisRequest("Bonjour", "fr-FR", "s-1"));

        assertThat(result.status()).isEqualTo(SpeechSynthesisStatus.UNSUPPORTED);
        assertThat(result.audio()).isEmpty();
    }

    @Test
    void failedResultNeverPretendsAudioWasProduced() {
        SpeechSynthesisService stub = request -> SpeechSynthesisResult.failed("en-IN", 7L);

        SpeechSynthesisResult result = stub.synthesize(new SpeechSynthesisRequest("Hello", "en-IN", "s-1"));

        assertThat(result.status()).isEqualTo(SpeechSynthesisStatus.FAILED);
        assertThat(result.audio()).isEmpty();
    }

    @Test
    void fallbackNeverClaimsSuccessForAnySupportedLanguage() {
        for (SupportedLanguage language : SupportedLanguage.values()) {
            SpeechSynthesisResult result = fallback.synthesize(
                    new SpeechSynthesisRequest("Question", language.code(), "s-1"));
            assertThat(result.status()).isNotEqualTo(SpeechSynthesisStatus.SYNTHESIZED);
            assertThat(result.audio()).isEmpty();
        }
    }

    @Test
    void textIsNeverLoggedByTheContract() {
        SpeechSynthesisResult result = fallback.synthesize(
                new SpeechSynthesisRequest("private question text", "kn-IN", "s-1"));
        assertThat(result.status()).isEqualTo(SpeechSynthesisStatus.UNAVAILABLE);
    }
}