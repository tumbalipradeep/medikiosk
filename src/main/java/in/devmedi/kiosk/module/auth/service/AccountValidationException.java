package in.devmedi.kiosk.module.auth.service;

/**
 * Raised when an account lifecycle operation violates the account or password
 * policy (invalid input, bad current password, duplicate username, etc.).
 */
public class AccountValidationException extends RuntimeException {

    public AccountValidationException(String message) {
        super(message);
    }
}