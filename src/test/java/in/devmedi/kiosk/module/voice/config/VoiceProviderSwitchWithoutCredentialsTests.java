package in.devmedi.kiosk.module.voice.config;

import in.devmedi.kiosk.module.voice.provider.BhashiniSpeechRecognitionService;
import in.devmedi.kiosk.module.voice.speech.SpeechRecognitionService;
import in.devmedi.kiosk.module.voice.speech.UnavailableSpeechRecognitionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A provider switch without its credentials must never engage the real
 * provider: the deterministic UNAVAILABLE fallback is used instead, so the
 * patient can always continue by typing.
 */
@SpringBootTest(properties = {
        "medikiosk.asr-provider=bhashini",
        "medikiosk.tts-provider=bhashini"
})
class VoiceProviderSwitchWithoutCredentialsTests {

    @Autowired
    private SpeechRecognitionService asr;

    @Test
    void incompleteBhashiniConfigurationFallsBackToUnavailableProviders() {
        assertThat(asr).isInstanceOf(UnavailableSpeechRecognitionService.class);
        assertThat(asr).isNotInstanceOf(BhashiniSpeechRecognitionService.class);
    }
}