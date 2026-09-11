package in.devmedi.kiosk.module.voice.speech;

import in.devmedi.kiosk.module.voice.language.LanguageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Deterministic speech-recognition fallback used when no real ASR provider is
 * configured.
 *
 * <p>This deployment ships without Bhashini/ULCA (or any other) credentials, so
 * recognition must not be simulated. This service never invents patient speech:
 * it reports {@link SpeechRecognitionStatus#UNAVAILABLE} (no capability) or
 * {@link SpeechRecognitionStatus#UNSUPPORTED} (language outside the supported
 * set) with no transcript. The patient simply continues with typed input.</p>
 *
 * <p>Not a Spring bean on purpose: {@code VoiceConfig} selects exactly one
 * {@link SpeechRecognitionService} per context, so this fallback is constructed
 * there (and directly in tests) only when no real provider is configured.</p>
 */
public class UnavailableSpeechRecognitionService implements SpeechRecognitionService {

    private static final Logger log = LoggerFactory.getLogger(UnavailableSpeechRecognitionService.class);

    private final LanguageService languageService;

    public UnavailableSpeechRecognitionService(LanguageService languageService) {
        this.languageService = languageService;
    }

    @Override
    public SpeechRecognitionResult transcribe(SpeechRecognitionRequest request) {
        String language = request.language();
        long start = System.nanoTime();
        if (!languageService.isSupported(language)) {
            log.debug("speech.asr status=UNSUPPORTED language={}", language);
            return SpeechRecognitionResult.unsupported(language);
        }
        log.debug("speech.asr status=UNAVAILABLE language={} durationMs={}",
                language, (System.nanoTime() - start) / 1_000_000L);
        return SpeechRecognitionResult.unavailable(language);
    }
}