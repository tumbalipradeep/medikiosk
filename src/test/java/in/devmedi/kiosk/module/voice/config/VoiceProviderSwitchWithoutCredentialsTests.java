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
 * patient can always continue by typing. WC adds: the resolved provider names
 * and the setup hint must stay truthful for every switch/credential state.
 */
@SpringBootTest(properties = {
        "medikiosk.asr-provider=bhashini",
        "medikiosk.tts-provider=bhashini"
})
class VoiceProviderSwitchWithoutCredentialsTests {

    @Autowired
    private SpeechRecognitionService asr;

    @Autowired
    private VoiceProperties voiceProperties;

    @Test
    void incompleteBhashiniConfigurationFallsBackToUnavailableProviders() {
        assertThat(asr).isInstanceOf(UnavailableSpeechRecognitionService.class);
        assertThat(asr).isNotInstanceOf(BhashiniSpeechRecognitionService.class);
    }

    @Test
    void resolvedProviderNamesNeverEchoARealProviderWithoutCompleteCredentials() {
        // The switch says bhashini, but the wiring binds the fallback — so the
        // reported provider name must be the fallback name, never 'bhashini'.
        assertThat(voiceProperties.resolvedAsrProvider())
                .isEqualTo(VoiceProperties.PROVIDER_UNAVAILABLE);
        assertThat(voiceProperties.resolvedTtsProvider())
                .isEqualTo(VoiceProperties.PROVIDER_UNAVAILABLE);
    }

    @Test
    void setupHintNamesTheExactMissingEnvironmentVariables() {
        String hint = voiceProperties.setupHint();
        assertThat(hint).isNotNull();
        // Switches are already set in this context; only credentials are missing.
        assertThat(hint).contains(VoiceProperties.ENV_BHASHINI_USER_ID)
                .contains(VoiceProperties.ENV_BHASHINI_API_KEY)
                .contains(VoiceProperties.ENV_BHASHINI_PIPELINE_ID);
        // Secret-free by construction: the hint cites variable NAMES, never values.
        assertThat(hint).doesNotContain("=");
    }

    @Test
    void unknownSwitchValueIsNotEchoedAsAProvider() {
        VoiceProperties misconfigured = new VoiceProperties();
        misconfigured.setAsrProvider("watson");
        misconfigured.setTtsProvider("bhashini");
        assertThat(misconfigured.asrProviderKnown()).isFalse();
        assertThat(misconfigured.ttsProviderKnown()).isTrue();
        assertThat(misconfigured.resolvedAsrProvider())
                .isEqualTo(VoiceProperties.PROVIDER_UNAVAILABLE);
    }

    @Test
    void fullyEngagedConfigurationNeedsNoSetupHint() {
        VoiceProperties engaged = new VoiceProperties();
        engaged.setAsrProvider(VoiceProperties.PROVIDER_BHASHINI);
        engaged.setTtsProvider(VoiceProperties.PROVIDER_BHASHINI);
        engaged.getBhashini().setUserId("user");
        engaged.getBhashini().setApiKey("key");
        engaged.getBhashini().setPipelineId("pipeline");
        assertThat(engaged.resolvedAsrProvider()).isEqualTo(VoiceProperties.PROVIDER_BHASHINI);
        assertThat(engaged.resolvedTtsProvider()).isEqualTo(VoiceProperties.PROVIDER_BHASHINI);
        assertThat(engaged.setupHint()).isNull();
    }
}