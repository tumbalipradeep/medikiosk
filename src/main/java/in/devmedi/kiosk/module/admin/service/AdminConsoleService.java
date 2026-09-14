package in.devmedi.kiosk.module.admin.service;

import in.devmedi.kiosk.module.admin.config.SystemSetting;
import in.devmedi.kiosk.module.admin.config.SystemSettingRepository;
import in.devmedi.kiosk.module.ai.provider.ClinicalAiProvider;
import in.devmedi.kiosk.module.auth.entity.Role;
import in.devmedi.kiosk.module.auth.entity.User;
import in.devmedi.kiosk.module.auth.repository.UserRepository;
import in.devmedi.kiosk.module.auth.service.AccountLifecycleService;
import in.devmedi.kiosk.module.auth.service.AccountValidationException;
import in.devmedi.kiosk.module.profile.entity.PatientProfile;
import in.devmedi.kiosk.module.profile.entity.PatientProfileRepository;
import in.devmedi.kiosk.module.profile.entity.PhysicianProfile;
import in.devmedi.kiosk.module.profile.entity.PhysicianProfileRepository;
import in.devmedi.kiosk.module.voice.language.LanguageService;
import in.devmedi.kiosk.module.voice.language.SupportedLanguage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Read/write facade for the administrator console. Account mutations are
 * delegated to {@link AccountLifecycleService} so every administrative action
 * reuses the same password, lock and activation rules as self-service flows;
 * this service only shapes the data for the administrative views and owns the
 * runtime {@code system_settings} key/value store.
 *
 * <p>No administrative operation here touches clinical provenance or clinical
 * content. The console exposes configuration and account state, never patient
 * answers, documents, or AI output.</p>
 */
@Service
public class AdminConsoleService {

    private final UserRepository userRepository;
    private final PatientProfileRepository patientProfileRepository;
    private final PhysicianProfileRepository physicianProfileRepository;
    private final AccountLifecycleService lifecycle;
    private final SystemSettingRepository settingRepository;
    private final LanguageService languageService;
    private final List<ClinicalAiProvider> aiProviders;

    public AdminConsoleService(UserRepository userRepository,
                               PatientProfileRepository patientProfileRepository,
                               PhysicianProfileRepository physicianProfileRepository,
                               AccountLifecycleService lifecycle,
                               SystemSettingRepository settingRepository,
                               LanguageService languageService,
                               List<ClinicalAiProvider> aiProviders) {
        this.userRepository = userRepository;
        this.patientProfileRepository = patientProfileRepository;
        this.physicianProfileRepository = physicianProfileRepository;
        this.lifecycle = lifecycle;
        this.settingRepository = settingRepository;
        this.languageService = languageService;
        this.aiProviders = List.copyOf(aiProviders);
    }

    // ─── Accounts ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AdminAccountRow> accounts() {
        return userRepository.findByOrderByIdDesc().stream().map(this::toRow).toList();
    }

    /**
     * @param role the role to filter by, or {@code null} for all accounts
     */
    @Transactional(readOnly = true)
    public List<AdminAccountRow> accounts(Role role) {
        List<User> users = role == null
                ? userRepository.findByOrderByIdDesc()
                : userRepository.findByRoleOrderByIdDesc(role);
        return users.stream().map(this::toRow).toList();
    }

    private AdminAccountRow toRow(User user) {
        String detail = switch (user.getRole()) {
            case PHYSICIAN -> physicianProfileRepository.findById(user.getId())
                    .map(p -> joinDetail(p.getDesignation(), p.getDepartment(), p.getOrganization()))
                    .orElse("");
            case PATIENT -> patientProfileRepository.findById(user.getId())
                    .map(PatientProfile::getPreferredLanguage)
                    .orElse("");
            case ADMIN -> "";
        };
        return new AdminAccountRow(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getRole().name(),
                user.isEnabled(),
                user.isLocked(),
                user.isMustChangePassword(),
                user.getCreatedAt(),
                detail);
    }

    private static String joinDetail(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                if (sb.length() > 0) {
                    sb.append(" \u00b7 ");
                }
                sb.append(part.trim());
            }
        }
        return sb.toString();
    }

    @Transactional(readOnly = true)
    public DashboardMetrics metrics() {
        List<AdminAccountRow> rows = accounts();
        long locked = rows.stream().filter(AdminAccountRow::locked).count();
        long deactivated = rows.stream().filter(r -> !r.enabled()).count();
        return new DashboardMetrics(
                userRepository.countByRole(Role.PATIENT),
                userRepository.countByRole(Role.PHYSICIAN),
                userRepository.countByRole(Role.ADMIN),
                locked,
                deactivated);
    }

    // ─── Account mutations (delegated) ────────────────────────────────

    public void activate(Long userId) {
        lifecycle.activate(userId);
    }

    public void deactivate(Long userId) {
        lifecycle.deactivate(userId);
    }

    public void lock(Long userId) {
        lifecycle.lockAccount(userId);
    }

    public void unlock(Long userId) {
        lifecycle.unlockAccount(userId);
    }

    public String resetPassword(Long userId) {
        return lifecycle.resetPasswordByAdmin(userId);
    }

    public String provisionPhysician(String username,
                                     String displayName,
                                     String qualification,
                                     String designation,
                                     String department,
                                     String organization,
                                     String registrationNumber,
                                     String email,
                                     String phone) {
        AccountLifecycleService.ProvisionedPhysician provisioned = lifecycle.provisionPhysician(
                username, displayName, qualification, designation, department,
                organization, registrationNumber, email, phone);
        return provisioned.temporaryPassword();
    }

    // ─── Physician profile maintenance ────────────────────────────────

    @Transactional(readOnly = true)
    public PhysicianProfile physicianProfile(Long userId) {
        User user = lifecycle.requireUser(userId);
        if (user.getRole() != Role.PHYSICIAN) {
            throw new AccountValidationException("Account is not a physician: " + user.getUsername());
        }
        return physicianProfileRepository.findById(userId)
                .orElseGet(() -> PhysicianProfile.create(user));
    }

    @Transactional
    public void updatePhysicianProfile(Long userId,
                                       String qualification,
                                       String designation,
                                       String department,
                                       String organization,
                                       String registrationNumber,
                                       String email,
                                       String phone) {
        User user = lifecycle.requireUser(userId);
        if (user.getRole() != Role.PHYSICIAN) {
            throw new AccountValidationException("Account is not a physician: " + user.getUsername());
        }
        PhysicianProfile profile = physicianProfileRepository.findById(userId)
                .orElseGet(() -> PhysicianProfile.create(user));
        profile.setQualification(qualification);
        profile.setDesignation(designation);
        profile.setDepartment(department);
        profile.setOrganization(organization);
        profile.setRegistrationNumber(registrationNumber);
        profile.setEmail(email);
        profile.setPhone(phone);
        physicianProfileRepository.save(profile);
    }

    // ─── System settings ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<SystemSetting> settings() {
        return settingRepository.findAll().stream()
                .sorted((a, b) -> a.getKey().compareToIgnoreCase(b.getKey()))
                .toList();
    }

    /**
     * Creates or updates one runtime setting. When the key is a configured
     * language selector, the value is validated against the language model so
     * an unsupported language can never be stored.
     */
    @Transactional
    public void saveSetting(String key, String value, Long administratorId) {
        if (key == null || key.isBlank()) {
            throw new AccountValidationException("Setting key must not be empty.");
        }
        if (value == null || value.length() > 500) {
            throw new AccountValidationException("Setting value must be present (max 500 characters).");
        }
        validateSetting(key, value);
        String trimmedKey = key.trim();
        User admin = lifecycle.requireUser(administratorId);
        SystemSetting setting = settingRepository.findByKey(trimmedKey)
                .orElseGet(() -> new SystemSetting(trimmedKey, value, null));
        setting.setValue(value);
        setting.setUpdatedBy(admin);
        settingRepository.save(setting);
    }

    private void validateSetting(String key, String value) {
        if ("kiosk.default_language".equals(key.trim()) && !languageService.isSupported(value)) {
            throw new AccountValidationException(
                    "Unsupported language code: " + value + ". Supported: " + supportedLanguageCodes());
        }
    }

    @Transactional
    public void deleteSetting(String key) {
        settingRepository.findByKey(key).ifPresent(settingRepository::delete);
    }

    private String supportedLanguageCodes() {
        return String.join(", ", languageService.supportedLanguages().stream()
                .map(SupportedLanguage::code).toList());
    }

    // ─── Read-only effective configuration ────────────────────────────

    @Transactional(readOnly = true)
    public List<LanguageOption> supportedLanguages() {
        return languageService.supportedLanguages().stream()
                .map(l -> new LanguageOption(l.code(), l.nativeName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AiProviderStatus> aiProviders() {
        return aiProviders.stream()
                .map(p -> new AiProviderStatus(p.getName(), p.isEnabled()))
                .sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
                .toList();
    }

    // ─── View records ─────────────────────────────────────────────────

    public record DashboardMetrics(long patients,
                                   long physicians,
                                   long administrators,
                                   long lockedAccounts,
                                   long deactivatedAccounts) {
    }

    public record LanguageOption(String code, String nativeName) {
    }

    public record AiProviderStatus(String name, boolean enabled) {
    }
}
