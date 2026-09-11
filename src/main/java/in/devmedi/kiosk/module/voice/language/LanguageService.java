package in.devmedi.kiosk.module.voice.language;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Central service for the patient language model.
 *
 * <p>MediKiosk supports exactly five patient languages (English, Hindi, Telugu,
 * Tamil, Kannada). English is the deterministic default: whenever no language
 * is specified, requests resolve to English. Unknown or malformed language
 * codes are never silently accepted — {@link #resolve(String)} throws
 * {@link UnsupportedLanguageException} so callers can fail safely at their
 * boundary.</p>
 *
 * <p>Language selection is deliberately independent from clinical progression:
 * this service only resolves which language the patient-facing layer uses. It
 * has no access to, and never touches, the clinical question pipeline.</p>
 */
@Service
public class LanguageService {

    /**
     * @return all supported languages in canonical order (English first)
     */
    public List<SupportedLanguage> supportedLanguages() {
        List<SupportedLanguage> languages = new ArrayList<>();
        for (SupportedLanguage language : SupportedLanguage.values()) {
            languages.add(language);
        }
        return List.copyOf(languages);
    }

    /**
     * Finds a supported language by its code (case-insensitive, trimmed).
     * An absent or blank code yields an empty result; it is never treated as
     * an arbitrary unsupported value.
     *
     * @param code the requested language code (for example {@code te-IN})
     * @return the matching supported language, or empty when unsupported
     */
    public Optional<SupportedLanguage> find(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        for (SupportedLanguage language : SupportedLanguage.values()) {
            if (language.code().equalsIgnoreCase(normalized)) {
                return Optional.of(language);
            }
        }
        return Optional.empty();
    }

    /**
     * @param code the requested language code
     * @return whether the code identifies a supported patient language
     */
    public boolean isSupported(String code) {
        return find(code).isPresent();
    }

    /**
     * Resolves the selected language, failing safely on unsupported codes.
     *
     * @param code the requested language code, or {@code null}/{@code blank}
     *             to mean "not selected"
     * @return the supported language; English when no language is selected
     * @throws UnsupportedLanguageException when the code is present but unsupported
     */
    public SupportedLanguage resolve(String code) {
        if (code == null || code.isBlank()) {
            return SupportedLanguage.ENGLISH;
        }
        return find(code).orElseThrow(() -> new UnsupportedLanguageException(code));
    }

    /**
     * Convenience resolver returning the BCP-47 tag of the resolved language.
     *
     * @param code the requested language code
     * @return the resolved language's BCP-47 tag (English by default)
     * @throws UnsupportedLanguageException when the code is present but unsupported
     */
    public String bcp47(String code) {
        return resolve(code).bcp47();
    }
}