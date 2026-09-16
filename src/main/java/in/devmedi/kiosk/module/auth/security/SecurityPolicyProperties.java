package in.devmedi.kiosk.module.auth.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Account security policy, externalised and adjustable per deployment:
 * lockout thresholds/duration and password-strength requirements.
 */
@ConfigurationProperties(prefix = "medikiosk.security")
public class SecurityPolicyProperties {

    /** Failed login attempts before the account is temporarily locked. */
    private int maxFailedLoginAttempts = 5;

    /** Minutes an account stays locked after exceeding the failed-attempt cap. */
    private long lockoutDurationMinutes = 15;

    /** Minimum password length — the only mandatory password rule. */
    private int minPasswordLength = 6;

    /** Minimum password length for admin-provisioned temporary passwords. */
    private int minTemporaryPasswordLength = 12;

    /** Passwords must contain at least one uppercase character (optional hardening flag). */
    private boolean requireUppercase = false;

    /** Passwords must contain at least one digit (optional hardening flag). */
    private boolean requireDigit = false;

    /** Passwords must contain at least one non-alphanumeric character (optional hardening flag). */
    private boolean requireSpecial = false;

    public int getMaxFailedLoginAttempts() { return maxFailedLoginAttempts; }
    public void setMaxFailedLoginAttempts(int maxFailedLoginAttempts) { this.maxFailedLoginAttempts = maxFailedLoginAttempts; }

    public long getLockoutDurationMinutes() { return lockoutDurationMinutes; }
    public void setLockoutDurationMinutes(long lockoutDurationMinutes) { this.lockoutDurationMinutes = lockoutDurationMinutes; }

    public int getMinPasswordLength() { return minPasswordLength; }
    public void setMinPasswordLength(int minPasswordLength) { this.minPasswordLength = minPasswordLength; }

    public int getMinTemporaryPasswordLength() { return minTemporaryPasswordLength; }
    public void setMinTemporaryPasswordLength(int minTemporaryPasswordLength) { this.minTemporaryPasswordLength = minTemporaryPasswordLength; }

    public boolean isRequireUppercase() { return requireUppercase; }
    public void setRequireUppercase(boolean requireUppercase) { this.requireUppercase = requireUppercase; }

    public boolean isRequireDigit() { return requireDigit; }
    public void setRequireDigit(boolean requireDigit) { this.requireDigit = requireDigit; }

    public boolean isRequireSpecial() { return requireSpecial; }
    public void setRequireSpecial(boolean requireSpecial) { this.requireSpecial = requireSpecial; }
}