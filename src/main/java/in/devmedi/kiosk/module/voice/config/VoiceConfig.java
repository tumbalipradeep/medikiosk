package in.devmedi.kiosk.module.voice.config;

import in.devmedi.kiosk.module.voice.language.LanguageService;
import in.devmedi.kiosk.module.voice.provider.BhashiniSpeechRecognitionService;
import in.devmedi.kiosk.module.voice.provider.BhashiniSpeechSynthesisService;
import in.devmedi.kiosk.module.voice.speech.SpeechRecognitionService;
import in.devmedi.kiosk.module.voice.speech.SpeechSynthesisService;
import in.devmedi.kiosk.module.voice.speech.UnavailableSpeechRecognitionService;
import in.devmedi.kiosk.module.voice.speech.UnavailableSpeechSynthesisService;
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

    @Bean
    public SpeechRecognitionService speechRecognitionService(VoiceProperties props,
                                                             LanguageService languageService,
                                                             RestClient.Builder restClientBuilder) {
        if (props.asrUsesBhashini() && props.getBhashini().isComplete()) {
            return new BhashiniSpeechRecognitionService(
                    props.getBhashini(), languageService, restClientBuilder);
        }
        return new UnavailableSpeechRecognitionService(languageService);
    }

    @Bean
    public SpeechSynthesisService speechSynthesisService(VoiceProperties props,
                                                         LanguageService languageService,
                                                         RestClient.Builder restClientBuilder) {
        if (props.ttsUsesBhashini() && props.getBhashini().isComplete()) {
            return new BhashiniSpeechSynthesisService(
                    props.getBhashini(), languageService, restClientBuilder);
        }
        return new UnavailableSpeechSynthesisService(languageService);
    }

    }