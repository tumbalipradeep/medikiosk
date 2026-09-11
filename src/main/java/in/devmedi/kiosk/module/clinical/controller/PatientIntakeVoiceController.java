package in.devmedi.kiosk.module.clinical.controller;

import in.devmedi.kiosk.module.voice.language.LanguageService;
import in.devmedi.kiosk.module.voice.language.SupportedLanguage;
import in.devmedi.kiosk.module.voice.speech.SpeechRecognitionRequest;
import in.devmedi.kiosk.module.voice.speech.SpeechRecognitionResult;
import in.devmedi.kiosk.module.voice.speech.SpeechRecognitionStatus;
import in.devmedi.kiosk.module.voice.speech.SpeechSynthesisRequest;
import in.devmedi.kiosk.module.voice.speech.SpeechSynthesisResult;
import in.devmedi.kiosk.module.voice.speech.SpeechSynthesisStatus;
import in.devmedi.kiosk.module.voice.speech.SpeechRecognitionService;
import in.devmedi.kiosk.module.voice.speech.SpeechSynthesisService;
import in.devmedi.kiosk.module.voice.speech.VoiceLimits;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Locale;

/**
 * Audio transport for the patient intake voice: client-side speech-to-text
 * (mic upload) and text-to-speech (question reading).
 *
 * <p>These endpoints are the only place raw audio crosses the HTTP boundary.
 * They delegate to the provider-neutral {@link SpeechRecognitionService} /
 * {@link SpeechSynthesisService}; the clinical layer never sees audio bytes.
 * Both endpoints honour the language currently selected for the intake session
 * (or the explicit {@code language} parameter) and resolve deterministically to
 * English when nothing is selected.</p>
 *
 * <p><strong>Privacy &amp; security:</strong> uploaded audio is read fully into
 * memory, passed to the ASR service, and never persisted or logged. The TTS
 * endpoint returns synthesized audio as an opaque binary body, never as text
 * content. Requests are validated at the boundary (size, content type, text
 * length, language) before any provider call, and CSRF is enforced exactly like
 * the conversation endpoint. When no ASR/TTS provider is configured the patient
 * receives a clear, recoverable response and continues by typing.</p>
 */
@RestController
@RequestMapping("/patient/intake/voice")
public class PatientIntakeVoiceController {

    private static final Logger log = LoggerFactory.getLogger(PatientIntakeVoiceController.class);

    private static final MediaType WAVE_AUDIO = MediaType.parseMediaType("audio/wav");

    private final SpeechRecognitionService asr;
    private final SpeechSynthesisService tts;
    private final LanguageService languageService;

    public PatientIntakeVoiceController(SpeechRecognitionService asr,
                                        SpeechSynthesisService tts,
                                        LanguageService languageService) {
        this.asr = asr;
        this.tts = tts;
        this.languageService = languageService;
    }

    /**
     * Recognizes speech from an uploaded audio clip.
     *
     * @param audio   the raw audio clip (form field {@code audio})
     * @param language optional BCP-47 language; defaults to the session language
     * @param session the patient session
     * @return a {@link AsrResponse}{@code s} with a transcript when recognized,
     *         or a structured error when recognition is not available
     */
    @PostMapping(value = "/asr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> asr(@RequestParam("audio") MultipartFile audio,
                                 @RequestParam(value = "language", required = false) String language,
                                 HttpSession session) {
        SupportedLanguage selected = resolve(language, session);
        if (audio.isEmpty()) {
            return error(HttpStatus.BAD_REQUEST, SpeechRecognitionStatus.EMPTY.name(),
                    selected.bcp47(), "The recording is empty. Please type instead.");
        }
        String contentType = audio.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("audio/")) {
            return error(HttpStatus.BAD_REQUEST, "UNSUPPORTED_MEDIA",
                    selected.bcp47(), "The uploaded file must be an audio file.");
        }
        if (audio.getSize() > VoiceLimits.ASR_MAX_INPUT_BYTES) {
            return error(HttpStatus.PAYLOAD_TOO_LARGE, "TOO_LARGE",
                    selected.bcp47(), "The recording is too large. Please record a shorter clip.");
        }

        byte[] bytes;
        try {
            bytes = audio.getBytes();
        } catch (IOException ex) {
            log.warn("voice.asr status=BAD_READ language={}", selected.bcp47());
            return error(HttpStatus.BAD_GATEWAY, SpeechRecognitionStatus.FAILED.name(),
                    selected.bcp47(), "The recording could not be read. Please try again or type.");
        }

        SpeechRecognitionResult result = asr.transcribe(
                new SpeechRecognitionRequest(bytes, selected.bcp47(), session.getId()));
        return toAsrResponse(result);
    }

    /**
     * Synthesizes question audio for the text the patient sees.
     *
     * @param request the text and language to speak
     * @param session the patient session
     * @return raw synthesized audio on success, or a structured error otherwise
     */
    @PostMapping(value = "/tts", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> tts(@RequestBody TtsRequest request, HttpSession session) {
        String text = request == null ? null : request.text();
        SupportedLanguage selected = resolve(request == null ? null : request.language(), session);
        if (text == null || text.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "INVALID",
                    selected.bcp47(), "No text to speak.");
        }
        if (text.length() > VoiceLimits.TTS_MAX_TEXT_CHARS) {
            return error(HttpStatus.BAD_REQUEST, "TOO_LONG",
                    selected.bcp47(), "The text is too long to speak.");
        }

        SpeechSynthesisResult result = tts.synthesize(
                new SpeechSynthesisRequest(text, selected.bcp47(), session.getId()));
        return toTtsResponse(result);
    }

    private ResponseEntity<?> toAsrResponse(SpeechRecognitionResult result) {
        String language = result.language();
        switch (result.status()) {
            case TRANSCRIBED:
                return ResponseEntity.ok(new AsrResponse(SpeechRecognitionStatus.TRANSCRIBED.name(),
                        result.transcript() == null ? "" : result.transcript(), language));
            case EMPTY:
                return ResponseEntity.ok(new AsrResponse(SpeechRecognitionStatus.EMPTY.name(), "", language));
            case UNSUPPORTED:
                return error(HttpStatus.BAD_REQUEST, SpeechRecognitionStatus.UNSUPPORTED.name(),
                        language, "Speech recognition does not support this language yet. Please type.");
            case UNAVAILABLE:
                return error(HttpStatus.SERVICE_UNAVAILABLE, SpeechRecognitionStatus.UNAVAILABLE.name(),
                        language, "Speech recognition is unavailable right now. Please type instead.");
            case FAILED:
                return error(HttpStatus.BAD_GATEWAY, SpeechRecognitionStatus.FAILED.name(),
                        language, "Speech recognition failed. Please try again or type.");
            default:
                return error(HttpStatus.INTERNAL_SERVER_ERROR, SpeechRecognitionStatus.FAILED.name(),
                        language, "Speech recognition could not be completed.");
        }
    }

    private ResponseEntity<?> toTtsResponse(SpeechSynthesisResult result) {
        String language = result.language();
        switch (result.status()) {
            case SYNTHESIZED:
                if (result.audio().length == 0) {
                    return error(HttpStatus.BAD_GATEWAY, SpeechSynthesisStatus.FAILED.name(),
                            language, "The audio could not be generated. Please read the question on screen.");
                }
                return ResponseEntity.ok()
                        .contentType(WAVE_AUDIO)
                        .body(result.audio());
            case UNSUPPORTED:
                return error(HttpStatus.BAD_REQUEST, SpeechSynthesisStatus.UNSUPPORTED.name(),
                        language, "Reading aloud does not support this language yet.");
            case UNAVAILABLE:
                return error(HttpStatus.SERVICE_UNAVAILABLE, SpeechSynthesisStatus.UNAVAILABLE.name(),
                        language, "Reading aloud is unavailable right now. You can read the question on screen.");
            case FAILED:
                return error(HttpStatus.BAD_GATEWAY, SpeechSynthesisStatus.FAILED.name(),
                        language, "Reading aloud failed. Please read the question on screen.");
            default:
                return error(HttpStatus.INTERNAL_SERVER_ERROR, SpeechSynthesisStatus.FAILED.name(),
                        language, "Reading aloud could not be completed.");
        }
    }

    private ResponseEntity<VoiceError> error(HttpStatus status, String code, String language, String message) {
        return ResponseEntity.status(status).body(new VoiceError(code, language, message));
    }

    private SupportedLanguage resolve(String requested, HttpSession session) {
        String candidate = (requested == null || requested.isBlank())
                ? (String) session.getAttribute(PatientIntakeLanguageController.LANGUAGE_ATTRIBUTE)
                : requested.trim();
        if (candidate == null || candidate.isBlank()) {
            return SupportedLanguage.ENGLISH;
        }
        return languageService.find(candidate)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Unsupported language: " + candidate));
    }

    /** ASR outcome returned to the client. */
    public record AsrResponse(String status, String transcript, String language) {
    }

    /** TTS request payload. */
    public record TtsRequest(String text, String language) {
    }

    /** Structured error body used by both endpoints. */
    public record VoiceError(String status, String language, String message) {
    }
}