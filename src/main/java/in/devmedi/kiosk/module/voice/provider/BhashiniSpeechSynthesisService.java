package in.devmedi.kiosk.module.voice.provider;

import in.devmedi.kiosk.module.voice.config.VoiceProperties;
import in.devmedi.kiosk.module.voice.language.LanguageService;
import in.devmedi.kiosk.module.voice.speech.SpeechSynthesisRequest;
import in.devmedi.kiosk.module.voice.speech.SpeechSynthesisResult;
import in.devmedi.kiosk.module.voice.speech.SpeechSynthesisService;
import in.devmedi.kiosk.module.voice.speech.VoiceLimits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.util.Locale;
import java.util.function.LongSupplier;

/**
 * Real TTS adapter that synthesizes question audio through the Bhashini/ULCA
 * pipeline.
 *
 * <p>Activated by {@code VoiceConfig} only when
 * {@code MEDIKIOSK_TTS_PROVIDER=bhashini} and the Bhashini credentials are
 * complete. This adapter never pretends audio was produced: synthesis only ever
 * returns a success result with the provider's actual audio bytes, and no text,
 * audio, or session identifiers are ever logged.</p>
 */
public class BhashiniSpeechSynthesisService implements SpeechSynthesisService {

    private static final Logger log = LoggerFactory.getLogger(BhashiniSpeechSynthesisService.class);

    public static final String PROVIDER = "bhashini";

    private final VoiceProperties.Bhashini settings;
    private final LanguageService languageService;
    private final BhashiniGateway gateway;

    public BhashiniSpeechSynthesisService(VoiceProperties.Bhashini settings,
                                          LanguageService languageService,
                                          RestClient.Builder restClientBuilder) {
        this.settings = settings;
        this.languageService = languageService;
        this.gateway = new BhashiniGateway(settings, restClientBuilder);
    }

    @Override
    public SpeechSynthesisResult synthesize(SpeechSynthesisRequest request) {
        String language = request.language();
        long start = System.nanoTime();
        LongSupplier elapsed = () -> (System.nanoTime() - start) / 1_000_000L;

        if (!languageService.isSupported(language)) {
            log.debug("speech.tts status=UNSUPPORTED provider={} language={}",
                    PROVIDER, language);
            return SpeechSynthesisResult.unsupported(language);
        }
        if (!settings.isComplete()) {
            log.info("speech.tts status=UNAVAILABLE provider=bhashini reason=INCOMPLETE_CONFIG language={} durationMs={}",
                    language, elapsed.getAsLong());
            return SpeechSynthesisResult.unavailable(language);
        }

        String text = request.text();
        if (text == null || text.isBlank()) {
            log.warn("speech.tts status=FAILED reason=BLANK_TEXT provider={} language={} durationMs={}",
                    PROVIDER, language, elapsed.getAsLong());
            return SpeechSynthesisResult.failed(language, elapsed.getAsLong());
        }
        if (text.length() > VoiceLimits.TTS_MAX_TEXT_CHARS) {
            log.warn("speech.tts status=FAILED reason=TEXT_TOO_LONG provider={} language={} durationMs={}",
                    PROVIDER, language, elapsed.getAsLong());
            return SpeechSynthesisResult.failed(language, elapsed.getAsLong());
        }

        try {
            byte[] audio = gateway.synthesize(iso639(language), text);
            if (audio.length == 0) {
                log.warn("speech.tts status=FAILED reason=EMPTY_AUDIO provider={} language={} durationMs={}",
                        PROVIDER, language, elapsed.getAsLong());
                return SpeechSynthesisResult.failed(language, elapsed.getAsLong());
            }
            log.debug("speech.tts status=SYNTHESIZED provider={} language={} bytes={} durationMs={}",
                    PROVIDER, language, audio.length, elapsed.getAsLong());
            return SpeechSynthesisResult.synthesized(audio, language);
        } catch (BhashiniException ex) {
            if (ex.failure() == BhashiniFailure.UNCONFIGURED) {
                log.info("speech.tts status=UNAVAILABLE provider=bhashini reason=INCOMPLETE_CONFIG "
                        + "language={} durationMs={}", language, elapsed.getAsLong());
                return SpeechSynthesisResult.unavailable(language);
            }
            log.warn("speech.tts status=FAILED provider={} failure={} reason={} language={} durationMs={}",
                    PROVIDER, ex.failure(), ex.getMessage(), language, elapsed.getAsLong());
            return SpeechSynthesisResult.failed(language, elapsed.getAsLong());
        }
    }

    private static String iso639(String bcp47) {
        return bcp47.split("-")[0].toLowerCase(Locale.ROOT);
    }
}