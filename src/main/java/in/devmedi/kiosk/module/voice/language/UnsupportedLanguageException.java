package in.devmedi.kiosk.module.voice.language;

/**
 * Thrown when a caller requests a language code that is not part of the
 * supported patient-language set. The message only carries the offending code —
 * it is safe metadata and never contains speech or clinical content.
 */
public class UnsupportedLanguageException extends RuntimeException {

    public UnsupportedLanguageException(String code) {
        super("Unsupported patient language: " + (code == null ? "null" : code));
    }
}