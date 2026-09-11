package in.devmedi.kiosk.module.voice.config;

import in.devmedi.kiosk.module.voice.provider.BhashiniSpeechRecognitionService;
import in.devmedi.kiosk.module.voice.provider.BhashiniSpeechSynthesisService;
import in.devmedi.kiosk.module.voice.speech.SpeechRecognitionService;
import in.devmedi.kiosk.module.voice.speech.SpeechSynthesisService;
import in.devmedi.kiosk.module.voice.speech.UnavailableSpeechRecognitionService;
import in.devmedi.kiosk.module.voice.speech.UnavailableSpeechSynthesisService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that exactly one ASR provider and exactly one TTS provider are wired
 * per context, decided purely from configuration: a real provider engages only
 * when its switch and its credentials are all present.
 */
@SpringBootTest(properties = {
        "medikiosk.asr-provider=bhashini",
        "medikiosk.tts-provider=bhashini",
        "medikiosk.bhashini.user-id=some-user",
        "medikiosk.bhashini.api-key=some-key",
        "medikiosk.bhashini.pipeline-id=some-pipeline"
})
class BhashiniVoiceProviderActivationTests {

    @Autowired
    private SpeechRecognitionService asr;

    @Autowired
    private SpeechSynthesisService tts;

    @Test
    void completeBhashiniConfigurationActivatesRealProviders() {
        assertThat(asr).isInstanceOf(BhashiniSpeechRecognitionService.class);
        assertThat(tts).isInstanceOf(BhashiniSpeechSynthesisService.class);
    }
}