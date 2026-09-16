package in.devmedi.kiosk.module.auth.preferences;

/**
 * Thrown when a display-preferences value is outside its closed set. Rendered
 * as an accessible inline error by the account controller.
 */
public class DisplayPreferencesValidationException extends RuntimeException {

    public DisplayPreferencesValidationException(String message) {
        super(message);
    }
}
