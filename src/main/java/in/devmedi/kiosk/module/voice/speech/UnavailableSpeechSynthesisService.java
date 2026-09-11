package in.devmedi.kiosk.module.voice.speech;

import in.devmedi.kiosk.module.voice.language.LanguageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Deterministic text-to-speech fallback used when no real TTS provider is
 * configured.
 *
 * <p>This deployment ships without Bhashini/ULCA (or any other) credentials, so
 * synthesis must not be simulated. This service never pretends audio was
 * generated: it reports {@link SpeechSynthesisStatus#UNAVAILABLE} (no
 * capability) or {@link SpeechSynthesisStatus#UNSUPPORTED} (language outside
 * the supported set) and always returns an empty audio payload.</p>
 *
 * <p>Not a Spring bean on purpose: {@code VoiceConfig} selects exactly one
 * {@link SpeechSynthesisService} per context, so this fallback is constructed
 * there (and directly in tests) only when no real provider is configured.</p>
 */
public class UnavailableSpeechSynthesisService implements SpeechSynthesisService {

    private static final Logger log = LoggerFactory.getLogger(UnavailableSpeechSynthesisService.class);

    private final LanguageService languageService;

    public UnavailableSpeechSynthesisService(LanguageService languageService) {
        this.languageService = languageService;
    }

    @Override
    public SpeechSynthesisResult synthesize(SpeechSynthesisRequest request) {
        String language = request.language();
        long start = System.nanoTime();
        if (!languageService.isSupported(language)) {
            log.debug("speech.tts status=UNSUPPORTED language={}", language);
            return SpeechSynthesisResult.unsupported(language);
        }
        log.debug("speech.tts status=UNAVAILABLE language={} durationMs={}",
                language, (System.nanoTime() - start) / 1_000_000L);
        return SpeechSynthesisResult.unavailable(language);
    }
}