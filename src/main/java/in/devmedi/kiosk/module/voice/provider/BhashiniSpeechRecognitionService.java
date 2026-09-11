package in.devmedi.kiosk.module.voice.provider;

import in.devmedi.kiosk.module.voice.config.VoiceProperties;
import in.devmedi.kiosk.module.voice.language.LanguageService;
import in.devmedi.kiosk.module.voice.speech.SpeechRecognitionRequest;
import in.devmedi.kiosk.module.voice.speech.SpeechRecognitionResult;
import in.devmedi.kiosk.module.voice.speech.SpeechRecognitionService;
import in.devmedi.kiosk.module.voice.speech.VoiceLimits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.util.Locale;
import java.util.function.LongSupplier;

/**
 * Real ASR adapter that recognizes patient speech through the Bhashini/ULCA
 * pipeline.
 *
 * <p>Activated by {@code VoiceConfig} only when
 * {@code MEDIKIOSK_ASR_PROVIDER=bhashini} and the Bhashini credentials are
 * complete. All validation happens here before any external call, and every
 * provider failure is classified into a conservative
 * {@code SpeechRecognitionResult}. This adapter never invents a transcript, and
 * it never logs audio content or session identifiers — only language, duration,
 * and a coarse failure kind.</p>
 */
public class BhashiniSpeechRecognitionService implements SpeechRecognitionService {

    private static final Logger log = LoggerFactory.getLogger(BhashiniSpeechRecognitionService.class);

    public static final String PROVIDER = "bhashini";

    private final VoiceProperties.Bhashini settings;
    private final LanguageService languageService;
    private final BhashiniGateway gateway;

    public BhashiniSpeechRecognitionService(VoiceProperties.Bhashini settings,
                                            LanguageService languageService,
                                            RestClient.Builder restClientBuilder) {
        this.settings = settings;
        this.languageService = languageService;
        this.gateway = new BhashiniGateway(settings, restClientBuilder);
    }

    @Override
    public SpeechRecognitionResult transcribe(SpeechRecognitionRequest request) {
        String language = request.language();
        long start = System.nanoTime();
        LongSupplier elapsed = () -> (System.nanoTime() - start) / 1_000_000L;

        if (!languageService.isSupported(language)) {
            log.debug("speech.asr status=UNSUPPORTED provider={} language={}",
                    PROVIDER, language);
            return SpeechRecognitionResult.unsupported(language);
        }
        if (!settings.isComplete()) {
            log.info("speech.asr status=UNAVAILABLE provider=bhashini reason=INCOMPLETE_CONFIG language={} durationMs={}",
                    language, elapsed.getAsLong());
            return SpeechRecognitionResult.unavailable(language);
        }

        byte[] audio = request.audio();
        if (audio.length == 0) {
            log.debug("speech.asr status=EMPTY reason=NO_AUDIO language={} durationMs={}",
                    language, elapsed.getAsLong());
            return SpeechRecognitionResult.empty(language);
        }
        if (audio.length > VoiceLimits.ASR_MAX_INPUT_BYTES) {
            log.warn("speech.asr status=FAILED reason=TOO_LARGE provider={} language={} durationMs={}",
                    PROVIDER, language, elapsed.getAsLong());
            return SpeechRecognitionResult.failed(language, elapsed.getAsLong());
        }

        try {
            String transcript = gateway.recognize(iso639(language), audio);
            if (transcript == null || transcript.isBlank()) {
                log.debug("speech.asr status=EMPTY reason=NO_SPEECH provider={} language={} durationMs={}",
                        PROVIDER, language, elapsed.getAsLong());
                return SpeechRecognitionResult.empty(language);
            }
            log.debug("speech.asr status=TRANSCRIBED provider={} language={} durationMs={}",
                    PROVIDER, language, elapsed.getAsLong());
            return SpeechRecognitionResult.transcribed(transcript, language);
        } catch (BhashiniException ex) {
            if (ex.failure() == BhashiniFailure.UNCONFIGURED) {
                log.info("speech.asr status=UNAVAILABLE provider=bhashini reason=INCOMPLETE_CONFIG "
                        + "language={} durationMs={}", language, elapsed.getAsLong());
                return SpeechRecognitionResult.unavailable(language);
            }
            log.warn("speech.asr status=FAILED provider={} failure={} language={} durationMs={}",
                    PROVIDER, ex.failure(), language, elapsed.getAsLong());
            return SpeechRecognitionResult.failed(language, elapsed.getAsLong());
        }
    }

    private static String iso639(String bcp47) {
        return bcp47.split("-")[0].toLowerCase(Locale.ROOT);
    }
}