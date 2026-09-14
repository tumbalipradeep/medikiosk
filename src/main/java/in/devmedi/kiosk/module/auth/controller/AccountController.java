package in.devmedi.kiosk.module.auth.controller;

import in.devmedi.kiosk.module.auth.entity.Role;
import in.devmedi.kiosk.module.auth.entity.User;
import in.devmedi.kiosk.module.auth.security.ApplicationUserDetails;
import in.devmedi.kiosk.module.auth.security.SecurityPolicyProperties;
import in.devmedi.kiosk.module.auth.service.AccountLifecycleService;
import in.devmedi.kiosk.module.auth.service.AccountValidationException;
import in.devmedi.kiosk.module.profile.entity.PatientProfile;
import in.devmedi.kiosk.module.profile.entity.PhysicianProfile;
import in.devmedi.kiosk.module.profile.storage.ProfilePictureStorage;
import in.devmedi.kiosk.module.voice.language.LanguageService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Self-service account management of the currently signed-in user:
 * secure password change (including the forced first-login change for
 * admin-provisioned physician accounts), active-session control, and
 * role-appropriate profile editing (demographic/contact/preference data kept
 * strictly separate from clinical records).
 */
@Controller
@RequestMapping("/account")
public class AccountController {

    private final AccountLifecycleService accountLifecycleService;
    private final SecurityPolicyProperties securityPolicyProperties;
    private final SessionRegistry sessionRegistry;
    private final LanguageService languageService;
    private final ProfilePictureStorage pictureStorage;

    public AccountController(AccountLifecycleService accountLifecycleService,
                             SecurityPolicyProperties securityPolicyProperties,
                             SessionRegistry sessionRegistry,
                             LanguageService languageService,
                             ProfilePictureStorage pictureStorage) {
        this.accountLifecycleService = accountLifecycleService;
        this.securityPolicyProperties = securityPolicyProperties;
        this.sessionRegistry = sessionRegistry;
        this.languageService = languageService;
        this.pictureStorage = pictureStorage;
    }

    @GetMapping("/password")
    public String passwordChange(@RequestParam(required = false) boolean forced, Model model) {
        model.addAttribute("forced", forced);
        model.addAttribute("minPasswordLength", securityPolicyProperties.getMinPasswordLength());
        model.addAttribute("requireUppercase", securityPolicyProperties.isRequireUppercase());
        model.addAttribute("requireDigit", securityPolicyProperties.isRequireDigit());
        model.addAttribute("requireSpecial", securityPolicyProperties.isRequireSpecial());
        return "account/password";
    }

    @PostMapping("/password")
    public String updatePassword(@RequestParam String currentPassword,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirmPassword,
                                 @RequestParam(required = false) boolean forced,
                                 Authentication authentication,
                                 Model model) {
        ApplicationUserDetails details = requireDetails(authentication);
        model.addAttribute("forced", forced);
        model.addAttribute("minPasswordLength", securityPolicyProperties.getMinPasswordLength());
        model.addAttribute("requireUppercase", securityPolicyProperties.isRequireUppercase());
        model.addAttribute("requireDigit", securityPolicyProperties.isRequireDigit());
        model.addAttribute("requireSpecial", securityPolicyProperties.isRequireSpecial());

        if (newPassword == null || !newPassword.equals(confirmPassword)) {
            model.addAttribute("error", "New passwords do not match.");
            return "account/password";
        }
        if (forced) {
            try {
                accountLifecycleService.completeForcedPasswordChange(details.userId(), newPassword);
            } catch (AccountValidationException ex) {
                model.addAttribute("error", ex.getMessage());
                return "account/password";
            }
        } else {
            try {
                accountLifecycleService.changePassword(details.userId(), currentPassword, newPassword);
            } catch (AccountValidationException ex) {
                model.addAttribute("error", ex.getMessage());
                return "account/password";
            }
        }
        return "redirect:/account/sessions?passwordUpdated=true";
    }

    @GetMapping("/sessions")
    public String sessions(Authentication authentication, HttpSession currentSession, Model model) {
        ApplicationUserDetails details = requireDetails(authentication);
        List<SessionView> sessions = new ArrayList<>();
        String currentSessionId = currentSession.getId();
        for (SessionInformation info : sessionRegistry.getAllPrincipals().stream()
                .flatMap(p -> sessionRegistry.getAllSessions(p, false).stream()).toList()) {
            Object principal = info.getPrincipal();
            String username = (principal instanceof UserDetails userDetails) ? userDetails.getUsername()
                    : String.valueOf(principal);
            if (details.username().equals(username)) {
                sessions.add(new SessionView(info.getSessionId(), info.getLastRequest(),
                        info.getSessionId().equals(currentSessionId)));
            }
        }
        sessions.sort((a, b) -> b.lastRequest().compareTo(a.lastRequest()));
        model.addAttribute("sessions", sessions);
        return "account/sessions";
    }

    @PostMapping("/sessions/expire")
    public String expireOthers(@RequestParam(required = false) List<String> sessionIds,
                               HttpSession currentSession) {
        String currentSessionId = currentSession.getId();
        if (sessionIds != null) {
            for (String sessionId : sessionIds) {
                if (sessionId == null || sessionId.equals(currentSessionId)) {
                    continue;
                }
                SessionInformation info = sessionRegistry.getSessionInformation(sessionId);
                if (info != null) {
                    info.expireNow();
                }
            }
        }
        return "redirect:/account/sessions";
    }

    // ─── Profile ──────────────────────────────────────────────────────

    @GetMapping("/profile")
    public String profile(@RequestParam(required = false) boolean updated,
                          Authentication authentication,
                          Model model) {
        ApplicationUserDetails details = requireDetails(authentication);
        User account = accountLifecycleService.requireUser(details.userId());
        model.addAttribute("updated", updated);
        seedProfileModel(account, model);
        return "account/profile";
    }

    @PostMapping("/profile")
    public String updateProfile(@RequestParam(required = false) String dateOfBirth,
                                @RequestParam(required = false) String gender,
                                @RequestParam(required = false) String phone,
                                @RequestParam(required = false) String email,
                                @RequestParam(required = false) String addressLine1,
                                @RequestParam(required = false) String addressLine2,
                                @RequestParam(required = false) String city,
                                @RequestParam(required = false) String state,
                                @RequestParam(required = false) String postalCode,
                                @RequestParam(required = false) String country,
                                @RequestParam(required = false) String emergencyContactName,
                                @RequestParam(required = false) String emergencyContactPhone,
                                @RequestParam(required = false) String bloodGroup,
                                @RequestParam(required = false) String preferredLanguage,
                                @RequestParam(required = false) String accessibilityPrefs,
                                @RequestParam(required = false) String notificationPrefs,
                                @RequestParam(required = false) String theme,
                                @RequestParam(value = "profilePicture", required = false) MultipartFile profilePicture,
                                Authentication authentication,
                                Model model) {
        ApplicationUserDetails details = requireDetails(authentication);
        User account = accountLifecycleService.requireUser(details.userId());
        try {
            LocalDate dob = parseDate(dateOfBirth);
            String picture = storePicture(profilePicture, account.getUsername());
            if (account.getRole() == Role.PATIENT) {
                accountLifecycleService.updatePatientProfile(details.userId(),
                        new AccountLifecycleService.PatientProfileEdit(
                                dob, gender, phone, email, addressLine1, addressLine2, city, state,
                                postalCode, country, emergencyContactName, emergencyContactPhone,
                                bloodGroup, preferredLanguage, accessibilityPrefs, notificationPrefs, picture));
            } else if (account.getRole() == Role.PHYSICIAN) {
                accountLifecycleService.updatePhysicianProfile(details.userId(),
                        new AccountLifecycleService.PhysicianProfileEdit(
                                dob, phone, email, preferredLanguage, theme, notificationPrefs, picture));
            } else {
                throw new AccountValidationException("Administrator accounts are managed through the console.");
            }
            return "redirect:/account/profile?updated";
        } catch (AccountValidationException ex) {
            model.addAttribute("error", ex.getMessage());
            seedProfileModel(account, model);
            return "account/profile";
        }
    }

    /**
     * Serves the signed-in user's own profile picture. The stored name is
     * looked up on the owner's profile so no other account can read it.
     */
    @GetMapping("/profile/picture")
    public ResponseEntity<byte[]> profilePicture(Authentication authentication) {
        ApplicationUserDetails details = requireDetails(authentication);
        User account = accountLifecycleService.requireUser(details.userId());
        String stored = switch (account.getRole()) {
            case PATIENT -> accountLifecycleService.patientProfile(account.getId()).getProfilePicturePath();
            case PHYSICIAN -> accountLifecycleService.physicianProfile(account.getId()).getProfilePicturePath();
            case ADMIN -> null;
        };
        if (stored == null || stored.isBlank()) {
            return ResponseEntity.notFound().build();
        }
        Path path = pictureStorage.resolve(stored);
        if (!Files.isRegularFile(path)) {
            return ResponseEntity.notFound().build();
        }
        try {
            return ResponseEntity.ok()
                    .contentType(guessMediaType(stored))
                    .body(Files.readAllBytes(path));
        } catch (IOException ex) {
            return ResponseEntity.internalServerError().build();
        }
    }

    private void seedProfileModel(User account, Model model) {
        model.addAttribute("account", account);
        model.addAttribute("role", account.getRole().name());
        model.addAttribute("supportedLanguages", languageService.supportedLanguages().stream()
                .map(l -> new LanguageOption(l.code(), l.nativeName(), l.label())).toList());
        switch (account.getRole()) {
            case PATIENT -> model.addAttribute("patientProfile",
                    accountLifecycleService.patientProfile(account.getId()));
            case PHYSICIAN -> model.addAttribute("physicianProfile",
                    accountLifecycleService.physicianProfile(account.getId()));
            case ADMIN -> { /* read-only; managed via the console */ }
        }
    }

    private String storePicture(MultipartFile file, String ownerUsername) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType();
        if (!ProfilePictureStorage.allowedImageTypes().contains(contentType)) {
            throw new AccountValidationException("Profile picture must be a JPG, PNG, or WebP image.");
        }
        if (file.getSize() > ProfilePictureStorage.maxBytes()) {
            throw new AccountValidationException("Profile picture must be 2 MB or smaller.");
        }
        try {
            return pictureStorage.store(file, file.getOriginalFilename());
        } catch (IOException ex) {
            throw new AccountValidationException("Profile picture could not be saved.");
        }
    }

    private static LocalDate parseDate(String dateOfBirth) {
        if (dateOfBirth == null || dateOfBirth.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(dateOfBirth.trim());
        } catch (DateTimeParseException ex) {
            throw new AccountValidationException("Date of birth must use the format YYYY-MM-DD.");
        }
    }

    private static MediaType guessMediaType(String stored) {
        String name = stored.toLowerCase();
        if (name.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (name.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        return MediaType.IMAGE_JPEG;
    }

    private static ApplicationUserDetails requireDetails(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof ApplicationUserDetails details) {
            return details;
        }
        throw new IllegalStateException("Principal is not an ApplicationUserDetails");
    }

    /** Read-only view model of one of the user's active sessions. */
    public record SessionView(String sessionId, java.util.Date lastRequest, boolean current) {
    }

    /** View model of one supported patient-facing language for a select. */
    public record LanguageOption(String code, String nativeName, String label) {
    }
}