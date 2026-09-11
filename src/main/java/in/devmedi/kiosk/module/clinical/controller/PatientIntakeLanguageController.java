package in.devmedi.kiosk.module.clinical.controller;

import in.devmedi.kiosk.module.voice.language.LanguageService;
import in.devmedi.kiosk.module.voice.language.SupportedLanguage;
import in.devmedi.kiosk.module.voice.language.UnsupportedLanguageException;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Patient-facing language selection for the clinical intake conversation.
 *
 * <p>Language is presentation-layer state: it only controls what wording the
 * patient sees and hears. It is stored in the HTTP session scoped to the
 * current intake session, is fully independent from clinical progression, and
 * never touches question identity, objective, section, ordering, completion,
 * or red-flag evaluation. Unsupported codes are rejected with a client error
 * and never silently stored.</p>
 */
@RestController
@RequestMapping("/patient/intake")
public class PatientIntakeLanguageController {

    /** HTTP session attribute holding the selected language code. */
    public static final String LANGUAGE_ATTRIBUTE = "patientIntakeLanguageCode";

    private final LanguageService languageService;

    public PatientIntakeLanguageController(LanguageService languageService) {
        this.languageService = languageService;
    }

    /** All request-supported patient languages, English first. */
    @GetMapping("/languages")
    public List<LanguageSelection> languages() {
        return languageService.supportedLanguages().stream()
                .map(PatientIntakeLanguageController::selection)
                .toList();
    }

    /** The currently selected language for this intake session (English by default). */
    @GetMapping("/language")
    public LanguageSelection current(HttpSession session) {
        return selection(resolve(session));
    }

    /**
     * Changes the language for the current intake session. The clinical
     * conversation state is untouched: answering, ordering, and completion
     * continue exactly as before.
     *
     * @param request the requested language code
     * @return the newly selected language
     */
    @PostMapping("/language")
    public LanguageSelection select(@RequestBody LanguageSelectionRequest request, HttpSession session) {
        SupportedLanguage language;
        try {
            language = languageService.resolve(request == null ? null : request.language());
        } catch (UnsupportedLanguageException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
        session.setAttribute(LANGUAGE_ATTRIBUTE, language.code());
        return selection(language);
    }

    private SupportedLanguage resolve(HttpSession session) {
        return languageService.resolve((String) session.getAttribute(LANGUAGE_ATTRIBUTE));
    }

    private static LanguageSelection selection(SupportedLanguage language) {
        return new LanguageSelection(language.code(), language.label(), language.bcp47());
    }

    /** Selection payload returned to the UI. */
    public record LanguageSelection(String code, String label, String bcp47) {
    }

    /** Selection request payload. */
    public record LanguageSelectionRequest(String language) {
    }
}