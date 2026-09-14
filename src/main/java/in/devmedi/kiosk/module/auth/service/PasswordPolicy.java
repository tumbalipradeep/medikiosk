package in.devmedi.kiosk.module.auth.service;

import in.devmedi.kiosk.module.auth.security.SecurityPolicyProperties;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

/**
 * Deterministic password-strength policy. Used for both user-chosen passwords
 * and admin-generated temporary passwords, so no weak credential can enter the
 * system.
 */
@Service
public class PasswordPolicy {

    private static final String UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijklmnopqrstuvwxyz";
    private static final String DIGITS = "0123456789";
    private static final String SPECIAL = "!@#$%^&*()-_=+[]{};:,.?/~";

    private final SecurityPolicyProperties properties;
    private final SecureRandom random = new SecureRandom();

    public PasswordPolicy(SecurityPolicyProperties properties) {
        this.properties = properties;
    }

    public PasswordCheck validate(String password) {
        if (password == null || password.isBlank()) {
            return PasswordCheck.fail("Password must not be empty.");
        }
        if (password.length() < properties.getMinPasswordLength()) {
            return PasswordCheck.fail("Password must be at least "
                    + properties.getMinPasswordLength() + " characters.");
        }
        if (containsWhitespace(password)) {
            return PasswordCheck.fail("Password must not contain spaces.");
        }
        if (properties.isRequireUppercase() && !containsAny(password, UPPER)) {
            return PasswordCheck.fail("Password must contain an uppercase letter.");
        }
        if (properties.isRequireDigit() && !containsAny(password, DIGITS)) {
            return PasswordCheck.fail("Password must contain a digit.");
        }
        if (properties.isRequireSpecial() && !containsAny(password, SPECIAL)) {
            return PasswordCheck.fail("Password must contain a special character.");
        }
        return PasswordCheck.ok();
    }

    /**
     * Generates a temporary password matching the temporary-password length
     * policy and the same character-class requirements.
     */
    public String generateTemporary() {
        int length = Math.max(properties.getMinTemporaryPasswordLength(),
                properties.getMinPasswordLength());
        StringBuilder sb = new StringBuilder(length);
        sb.append(character(UPPER)).append(character(DIGITS)).append(character(SPECIAL));
        String pool = UPPER + LOWER + DIGITS + SPECIAL;
        while (sb.length() < length) {
            sb.append(pool.charAt(random.nextInt(pool.length())));
        }
        return sb.toString();
    }

    private static boolean containsAny(String value, String chars) {
        for (int i = 0; i < chars.length(); i++) {
            if (value.indexOf(chars.charAt(i)) >= 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private char character(String pool) {
        return pool.charAt(random.nextInt(pool.length()));
    }
}