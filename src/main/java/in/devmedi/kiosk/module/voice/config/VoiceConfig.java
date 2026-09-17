package in.devmedi.kiosk.module.voice.config;

import in.devmedi.kiosk.module.voice.language.LanguageService;
import in.devmedi.kiosk.module.voice.provider.BhashiniSpeechRecognitionService;
import in.devmedi.kiosk.module.voice.provider.BhashiniSpeechSynthesisService;
import in.devmedi.kiosk.module.voice.speech.SpeechRecognitionService;
import in.devmedi.kiosk.module.voice.speech.SpeechSynthesisService;
import in.devmedi.kiosk.module.voice.speech.UnavailableSpeechRecognitionService;
import in.devmedi.kiosk.module.voice.speech.UnavailableSpeechSynthesisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Wires the ASR/TTS services as exactly one bean per interface.
 *
 * <p>The active implementation is chosen from configuration, mirroring the AI
 * provider wiring: a real provider is engaged only when its switch is set and
 * its credentials are complete; otherwise the deterministic
 * {@code UNAVAILABLE} fallback is used. Because this config returns the single
 * bean for each interface, the fallback classes are deliberately not
 * stereotype-annotated — they are plain classes constructed here (and in
 * tests), so there is never more than one {@link SpeechRecognitionService} or
 * {@link SpeechSynthesisService} in the application context.</p>
 */
@Configuration
@EnableConfigurationProperties(VoiceProperties.class)
public class VoiceConfig {

    private static final Logger log = LoggerFactory.getLogger(VoiceConfig.class);

    /**
     * Startup-time truthfulness check: a switch naming a provider this build
     * does not implement, or a real switch without complete credentials, would
     * otherwise silently bind the UNAVAILABLE fallback. Logged once, here, so
     * the operator sees exactly why voice is not live and which environment
     * variable to change. Never logs credential values.
     */
    private static void warnIfMisconfigured(String direction, String switchValue,
                                            VoiceProperties props, boolean switchKnown,
                                            boolean providerEngaged) {
        if (!switchKnown) {
            log.warn("voice.{} provider switch '{}' is not a known provider {}; "
                            + "the deterministic UNAVAILABLE fallback is active. Set {} to '{}' "
                            + "(or '{}') to clear this warning.",
                    direction, switchValue, VoiceProperties.KNOWN_PROVIDERS,
                    direction.equals("asr")
                            ? VoiceProperties.ENV_ASR_PROVIDER : VoiceProperties.ENV_TTS_PROVIDER,
                    VoiceProperties.PROVIDER_BHASHINI, VoiceProperties.PROVIDER_UNAVAILABLE);
        } else if (!providerEngaged) {
            log.warn("voice.{} provider is '{}' but Bhashini credentials are incomplete; "
                            + "the UNAVAILABLE fallback is active and patients type instead. "
                            + "Set {}, {} and {} (never commit values) and restart.",
                    direction, switchValue,
                    VoiceProperties.ENV_BHASHINI_USER_ID,
                    VoiceProperties.ENV_BHASHINI_API_KEY,
                    VoiceProperties.ENV_BHASHINI_PIPELINE_ID);
        }
    }

    @Bean
    public SpeechRecognitionService speechRecognitionService(VoiceProperties props,
                                                             LanguageService languageService,
                                                             RestClient.Builder restClientBuilder) {
        boolean engaged = props.asrUsesBhashini() && props.getBhashini().isComplete();
        warnIfMisconfigured("asr", props.getAsrProvider(), props,
                props.asrProviderKnown(), engaged);
        if (engaged) {
            return new BhashiniSpeechRecognitionService(
                    props.getBhashini(), languageService, restClientBuilder);
        }
        return new UnavailableSpeechRecognitionService(languageService);
    }

    @Bean
    public SpeechSynthesisService speechSynthesisService(VoiceProperties props,
                                                         LanguageService languageService,
                                                         RestClient.Builder restClientBuilder) {
        boolean engaged = props.ttsUsesBhashini() && props.getBhashini().isComplete();
        warnIfMisconfigured("tts", props.getTtsProvider(), props,
                props.ttsProviderKnown(), engaged);
        if (engaged) {
            return new BhashiniSpeechSynthesisService(
                    props.getBhashini(), languageService, restClientBuilder);
        }
        return new UnavailableSpeechSynthesisService(languageService);
    }

    }