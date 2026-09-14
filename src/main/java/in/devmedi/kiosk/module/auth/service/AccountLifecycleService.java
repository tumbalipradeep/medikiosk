package in.devmedi.kiosk.module.auth.service;

import in.devmedi.kiosk.module.auth.entity.User;
import in.devmedi.kiosk.module.auth.repository.UserRepository;
import in.devmedi.kiosk.module.auth.security.SecurityPolicyProperties;
import in.devmedi.kiosk.module.profile.entity.PatientProfile;
import in.devmedi.kiosk.module.profile.entity.PatientProfileRepository;
import in.devmedi.kiosk.module.profile.entity.PhysicianProfile;
import in.devmedi.kiosk.module.profile.entity.PhysicianProfileRepository;
import in.devmedi.kiosk.module.voice.language.LanguageService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Account lifecycle for patients, physicians, and administrators.
 *
 * <p>Handles self-service patient registration, admin provisioning of
 * physician accounts (with a forced password change on first login), password
 * changes, temporary lockout after failed logins, manual lock/unlock,
 * activate/deactivate, and password resets. Every mutating operation writes
 * through the {@link User} entity, so the same BCrypt + account-state rules
 * apply everywhere. No administrative operation ever touches clinical
 * provenance or clinical data.</p>
 */
@Service
public class AccountLifecycleService {

    private final UserRepository userRepository;
    private final PatientProfileRepository patientProfileRepository;
    private final PhysicianProfileRepository physicianProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final SecurityPolicyProperties policy;
    private final LanguageService languageService;

    public AccountLifecycleService(UserRepository userRepository,
                                   PatientProfileRepository patientProfileRepository,
                                   PhysicianProfileRepository physicianProfileRepository,
                                   PasswordEncoder passwordEncoder,
                                   PasswordPolicy passwordPolicy,
                                   SecurityPolicyProperties policy,
                                   LanguageService languageService) {
        this.userRepository = userRepository;
        this.patientProfileRepository = patientProfileRepository;
        this.physicianProfileRepository = physicianProfileRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.policy = policy;
        this.languageService = languageService;
    }

    public SecurityPolicyProperties policy() {
        return policy;
    }

    // ─── Registration ────────────────────────────────────────────────

    @Transactional
    public User registerPatient(String username, String password, String displayName) {
        PasswordCheck check = validateRegistration(username, password, displayName);
        if (!check.valid()) {
            throw new AccountValidationException(check.message());
        }
        User user = new User(username, passwordEncoder.encode(password), displayName,
                in.devmedi.kiosk.module.auth.entity.Role.PATIENT);
        userRepository.save(user);
        patientProfileRepository.save(PatientProfile.create(user));
        return user;
    }

    /**
     * Admin provisions a physician account. The account starts with
     * {@code must_change_password = true}, so the physician must set their
     * own password on the first login and the admin never needs to know the
     * final credential.
     */
    @Transactional
    public ProvisionedPhysician provisionPhysician(String username,
                                                   String displayName,
                                                   String qualification,
                                                   String designation,
                                                   String department,
                                                   String organization,
                                                   String registrationNumber,
                                                   String email,
                                                   String phone) {
        if (userRepository.existsByUsername(username)) {
            throw new AccountValidationException("Username is already taken: " + username);
        }
        if (displayName == null || displayName.isBlank()) {
            throw new AccountValidationException("Display name must not be empty.");
        }
        String temporaryPassword = passwordPolicy.generateTemporary();
        User user = new User(username, passwordEncoder.encode(temporaryPassword), displayName,
                in.devmedi.kiosk.module.auth.entity.Role.PHYSICIAN);
        user.setMustChangePassword(true);
        userRepository.save(user);

        PhysicianProfile profile = PhysicianProfile.create(user);
        profile.setQualification(qualification);
        profile.setDesignation(designation);
        profile.setDepartment(department);
        profile.setOrganization(organization);
        profile.setRegistrationNumber(registrationNumber);
        profile.setEmail(email);
        profile.setPhone(phone);
        physicianProfileRepository.save(profile);
        return new ProvisionedPhysician(user, temporaryPassword);
    }

    // ─── Password management ──────────────────────────────────────────

    /**
     * Verifies the current password and updates it to a new policy-compliant
     * one, clearing the forced-change flag.
     */
    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AccountValidationException("Account not found."));
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new AccountValidationException("Current password is incorrect.");
        }
        applyNewPassword(user, newPassword);
    }

    /**
     * Sets a new password for a forced-change flow (must_change_password=true)
     * without requiring the current password again.
     */
    @Transactional
    public void completeForcedPasswordChange(Long userId, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AccountValidationException("Account not found."));
        if (!user.isMustChangePassword() && !user.isLocked()) {
            return;
        }
        applyNewPassword(user, newPassword);
    }

    @Transactional
    public void applyNewPasswordInternal(User user, String newPassword) {
        applyNewPassword(user, newPassword);
    }

    private void applyNewPassword(User user, String newPassword) {
        PasswordCheck check = passwordPolicy.validate(newPassword);
        if (!check.valid()) {
            throw new AccountValidationException(check.message());
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        user.resetFailedLogin();
        user.setLockedUntil(null);
        userRepository.save(user);
    }

    @Transactional
    public String resetPasswordByAdmin(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AccountValidationException("Account not found."));
        String temporaryPassword = passwordPolicy.generateTemporary();
        user.setPassword(passwordEncoder.encode(temporaryPassword));
        user.setMustChangePassword(true);
        user.resetFailedLogin();
        user.setLockedUntil(null);
        userRepository.save(user);
        return temporaryPassword;
    }

    // ─── Lockout / activation ─────────────────────────────────────────

    @Transactional
    public void recordFailedLogin(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            user.recordFailedLogin(policy.getMaxFailedLoginAttempts(),
                    Duration.ofMinutes(policy.getLockoutDurationMinutes()));
            userRepository.save(user);
        });
    }

    @Transactional
    public void clearFailedLogins(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            user.resetFailedLogin();
            userRepository.save(user);
        });
    }

    @Transactional
    public void lockAccount(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AccountValidationException("Account not found."));
        user.setLockedUntil(Instant.now().plus(Duration.ofDays(3650)));
        userRepository.save(user);
    }

    @Transactional
    public void unlockAccount(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AccountValidationException("Account not found."));
        user.resetFailedLogin();
        user.setLockedUntil(null);
        userRepository.save(user);
    }

    @Transactional
    public void activate(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AccountValidationException("Account not found."));
        user.setEnabled(true);
        userRepository.save(user);
    }

    @Transactional
    public void deactivate(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AccountValidationException("Account not found."));
        user.setEnabled(false);
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public List<User> accounts() {
        return userRepository.findByOrderByIdDesc();
    }

    @Transactional(readOnly = true)
    public User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AccountValidationException("Account not found."));
    }

    // ─── Profile self-service ─────────────────────────────────────────

    /**
     * Returns the signed-in patient's profile, creating an empty row on first
     * read so older provisioned accounts (which predate profiles) always have
     * one. Mutable demographic contact data lives here, never on clinical
     * records.
     */
    @Transactional(readOnly = true)
    public PatientProfile patientProfile(Long userId) {
        User user = requirePatient(userId);
        return patientProfileRepository.findByUserId(userId)
                .orElseGet(() -> PatientProfile.create(user));
    }

    /**
     * Returns the signed-in physician's profile, creating an empty row on
     * first read. Professional lines (qualification, designation, department,
     * organization, registration number) remain administrator-managed; the
     * physician edits contact, language, theme, and notification preferences.
     */
    @Transactional(readOnly = true)
    public PhysicianProfile physicianProfile(Long userId) {
        User user = requirePhysician(userId);
        return physicianProfileRepository.findByUserId(userId)
                .orElseGet(() -> PhysicianProfile.create(user));
    }

    @Transactional
    public void updatePatientProfile(Long userId, PatientProfileEdit edit) {
        PatientProfile profile = patientProfile(userId);
        profile.setDateOfBirth(validateDateOfBirth(edit.dateOfBirth()));
        profile.setGender(parseGender(edit.gender()));
        profile.setPhone(blankToNull(edit.phone()));
        profile.setEmail(validateEmail(edit.email()));
        profile.setAddressLine1(blankToNull(edit.addressLine1()));
        profile.setAddressLine2(blankToNull(edit.addressLine2()));
        profile.setCity(blankToNull(edit.city()));
        profile.setState(blankToNull(edit.state()));
        profile.setPostalCode(blankToNull(edit.postalCode()));
        profile.setCountry(blankToNull(edit.country()));
        profile.setEmergencyContactName(blankToNull(edit.emergencyContactName()));
        profile.setEmergencyContactPhone(blankToNull(edit.emergencyContactPhone()));
        profile.setBloodGroup(blankToNull(edit.bloodGroup()));
        profile.setPreferredLanguage(validateLanguage(edit.preferredLanguage()));
        profile.setAccessibilityPrefs(blankToNull(edit.accessibilityPrefs()));
        profile.setNotificationPrefs(blankToNull(edit.notificationPrefs()));
        if (edit.profilePicturePath() != null) {
            profile.setProfilePicturePath(edit.profilePicturePath());
        }
        patientProfileRepository.save(profile);
    }

    @Transactional
    public void updatePhysicianProfile(Long userId, PhysicianProfileEdit edit) {
        PhysicianProfile profile = physicianProfile(userId);
        profile.setDateOfBirth(validateDateOfBirth(edit.dateOfBirth()));
        profile.setPhone(blankToNull(edit.phone()));
        profile.setEmail(validateEmail(edit.email()));
        profile.setPreferredLanguage(validateLanguage(edit.preferredLanguage()));
        profile.setTheme(validateTheme(edit.theme()));
        profile.setNotificationPrefs(blankToNull(edit.notificationPrefs()));
        if (edit.profilePicturePath() != null) {
            profile.setProfilePicturePath(edit.profilePicturePath());
        }
        physicianProfileRepository.save(profile);
    }

    private User requirePatient(Long userId) {
        User user = requireUser(userId);
        if (user.getRole() != in.devmedi.kiosk.module.auth.entity.Role.PATIENT) {
            throw new AccountValidationException("Account is not a patient: " + user.getUsername());
        }
        return user;
    }

    private User requirePhysician(Long userId) {
        User user = requireUser(userId);
        if (user.getRole() != in.devmedi.kiosk.module.auth.entity.Role.PHYSICIAN) {
            throw new AccountValidationException("Account is not a physician: " + user.getUsername());
        }
        return user;
    }

    private static LocalDate validateDateOfBirth(LocalDate dateOfBirth) {
        if (dateOfBirth == null) {
            return null;
        }
        if (dateOfBirth.isAfter(LocalDate.now())) {
            throw new AccountValidationException("Date of birth cannot be in the future.");
        }
        return dateOfBirth;
    }

    private static PatientProfile.Gender parseGender(String gender) {
        if (gender == null || gender.isBlank()) {
            return null;
        }
        try {
            return PatientProfile.Gender.valueOf(gender.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new AccountValidationException(
                    "Invalid gender: " + gender + ". Use MALE, FEMALE, OTHER, or PREFER_NOT_TO_SAY.");
        }
    }

    private static String validateEmail(String email) {
        String normalized = blankToNull(email);
        if (normalized != null && !normalized.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new AccountValidationException("Email address does not look valid.");
        }
        return normalized;
    }

    private String validateLanguage(String code) {
        String normalized = blankToNull(code);
        if (normalized == null) {
            throw new AccountValidationException("Preferred language must be selected.");
        }
        if (!languageService.isSupported(normalized)) {
            throw new AccountValidationException("Unsupported language code: " + normalized);
        }
        return normalized;
    }

    private static String validateTheme(String theme) {
        String normalized = blankToNull(theme);
        if (normalized == null || !Set.of("system", "light", "dark").contains(normalized)) {
            throw new AccountValidationException("Theme must be one of: system, light, dark.");
        }
        return normalized;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    /** Editable patient profile fields (all optional unless noted). */
    public record PatientProfileEdit(LocalDate dateOfBirth,
                                     String gender,
                                     String phone,
                                     String email,
                                     String addressLine1,
                                     String addressLine2,
                                     String city,
                                     String state,
                                     String postalCode,
                                     String country,
                                     String emergencyContactName,
                                     String emergencyContactPhone,
                                     String bloodGroup,
                                     String preferredLanguage,
                                     String accessibilityPrefs,
                                     String notificationPrefs,
                                     String profilePicturePath) {
    }

    /** Editable physician profile fields (all optional unless noted). */
    public record PhysicianProfileEdit(LocalDate dateOfBirth,
                                       String phone,
                                       String email,
                                       String preferredLanguage,
                                       String theme,
                                       String notificationPrefs,
                                       String profilePicturePath) {
    }

    private PasswordCheck validateRegistration(String username, String password, String displayName) {
        List<String> problems = new ArrayList<>();
        if (username == null || username.isBlank() || username.length() < 3
                || !username.matches("[a-zA-Z0-9._-]+")) {
            problems.add("Username must be at least 3 characters using letters, digits, . _ - only.");
        }
        if (userRepository.existsByUsername(username)) {
            problems.add("Username is already taken: " + username);
        }
        if (displayName == null || displayName.isBlank() || displayName.length() > 200) {
            problems.add("Display name must not be empty (max 200 characters).");
        }
        PasswordCheck passwordCheck = passwordPolicy.validate(password);
        if (!passwordCheck.valid()) {
            problems.add(passwordCheck.message());
        }
        return problems.isEmpty()
                ? PasswordCheck.ok()
                : PasswordCheck.fail(String.join(" ", problems));
    }

    /** Result of provisioning a physician account (user + temporary password). */
    public record ProvisionedPhysician(User user, String temporaryPassword) {
    }
}