package in.devmedi.kiosk.module.physician.workspace;

/**
 * A physician review request failed validation.
 */
public class ReviewValidationException extends RuntimeException {

    public ReviewValidationException(String message) {
        super(message);
    }
}