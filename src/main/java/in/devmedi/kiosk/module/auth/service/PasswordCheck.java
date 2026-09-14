package in.devmedi.kiosk.module.auth.service;

/**
 * Password policy validation result.
 *
 * @param valid    whether the password satisfies the configured policy
 * @param message  human-readable explanation when invalid
 */
public record PasswordCheck(boolean valid, String message) {

    public static PasswordCheck ok() {
        return new PasswordCheck(true, null);
    }

    public static PasswordCheck fail(String message) {
        return new PasswordCheck(false, message);
    }
}