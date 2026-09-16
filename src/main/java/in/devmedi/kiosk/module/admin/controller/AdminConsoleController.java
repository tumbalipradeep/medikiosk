package in.devmedi.kiosk.module.admin.controller;

import in.devmedi.kiosk.module.admin.config.SystemSetting;
import in.devmedi.kiosk.module.admin.service.AdminAccountRow;
import in.devmedi.kiosk.module.admin.service.AdminConsoleService;
import in.devmedi.kiosk.module.auth.entity.Role;
import in.devmedi.kiosk.module.auth.security.ApplicationUserDetails;
import in.devmedi.kiosk.module.auth.service.AccountValidationException;
import in.devmedi.kiosk.module.profile.entity.PhysicianProfile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Administrator console: patient and physician account administration,
 * physician provisioning and profile maintenance, and the runtime system
 * settings store. Routes live under {@code /admin/**} so {@code ROLE_ADMIN} is
 * required by the existing {@code SecurityConfig} path rule.
 *
 * <p>All account mutations delegate to the account lifecycle service; the
 * console adds no separate authorization path and never exposes clinical
 * content.</p>
 */
@Controller
public class AdminConsoleController {

    private final AdminConsoleService console;

    public AdminConsoleController(AdminConsoleService console) {
        this.console = console;
    }

    @GetMapping("/admin/home")
    public String home(@AuthenticationPrincipal ApplicationUserDetails user, Model model) {
        model.addAttribute("user", user);
        model.addAttribute("role", "Administrator");
        model.addAttribute("metrics", console.metrics());
        model.addAttribute("recentAccounts", console.accounts().stream().limit(5).toList());
        model.addAttribute("aiProviders", console.aiProviders());
        model.addAttribute("capabilities", console.capabilitySummary());
        var recentAuditEvents = console.recentAuditEvents(10);
        model.addAttribute("recentAuditEvents", recentAuditEvents);
        model.addAttribute("hasAuditEvents", !recentAuditEvents.isEmpty());
        return "admin/home";
    }

    // ─── Accounts ─────────────────────────────────────────────────────

    @GetMapping("/admin/accounts")
    public String accounts(@RequestParam(name = "role", required = false) String role,
                           @AuthenticationPrincipal ApplicationUserDetails user,
                           Model model) {
        Role filter = parseRole(role);
        List<AdminAccountRow> rows = console.accounts(filter);
        model.addAttribute("user", user);
        model.addAttribute("role", "Administrator");
        model.addAttribute("accounts", rows);
        model.addAttribute("roleFilter", filter == null ? "" : filter.name());
        model.addAttribute("hasAccounts", !rows.isEmpty());
        return "admin/accounts";
    }

    private static Role parseRole(String role) {
        if (role == null || role.isBlank()) {
            return null;
        }
        try {
            return Role.valueOf(role.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    @PostMapping("/admin/accounts/{id}/activate")
    public String activate(@PathVariable Long id, RedirectAttributes flash) {
        return mutate(flash, () -> console.activate(id), "Account activated.");
    }

    @PostMapping("/admin/accounts/{id}/deactivate")
    public String deactivate(@PathVariable Long id, RedirectAttributes flash) {
        return mutate(flash, () -> console.deactivate(id), "Account deactivated.");
    }

    @PostMapping("/admin/accounts/{id}/lock")
    public String lock(@PathVariable Long id, RedirectAttributes flash) {
        return mutate(flash, () -> console.lock(id), "Account locked.");
    }

    @PostMapping("/admin/accounts/{id}/unlock")
    public String unlock(@PathVariable Long id, RedirectAttributes flash) {
        return mutate(flash, () -> console.unlock(id), "Account unlocked.");
    }

    @PostMapping("/admin/accounts/{id}/reset-password")
    public String resetPassword(@PathVariable Long id, RedirectAttributes flash) {
        try {
            String temporaryPassword = console.resetPassword(id);
            flash.addFlashAttribute("success",
                    "Temporary password generated. Share it securely; the user must change it on next login.");
            flash.addFlashAttribute("temporaryPassword", temporaryPassword);
        } catch (AccountValidationException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/accounts";
    }

    private String mutate(RedirectAttributes flash, Runnable action, String success) {
        try {
            action.run();
            flash.addFlashAttribute("success", success);
        } catch (AccountValidationException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/accounts";
    }

    // ─── Physician provisioning + profile ─────────────────────────────

    @PostMapping("/admin/physicians/provision")
    public String provisionPhysician(@RequestParam String username,
                                     @RequestParam String displayName,
                                     @RequestParam(required = false, defaultValue = "") String qualification,
                                     @RequestParam(required = false, defaultValue = "") String designation,
                                     @RequestParam(required = false, defaultValue = "") String department,
                                     @RequestParam(required = false, defaultValue = "") String organization,
                                     @RequestParam(required = false, defaultValue = "") String registrationNumber,
                                     @RequestParam(required = false, defaultValue = "") String email,
                                     @RequestParam(required = false, defaultValue = "") String phone,
                                     RedirectAttributes flash) {
        try {
            String temporaryPassword = console.provisionPhysician(username, displayName,
                    qualification, designation, department, organization,
                    registrationNumber, email, phone);
            flash.addFlashAttribute("success",
                    "Physician account provisioned. Temporary password is shown once below.");
            flash.addFlashAttribute("temporaryPassword", temporaryPassword);
            flash.addFlashAttribute("temporaryPasswordFor", username);
        } catch (AccountValidationException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/accounts?role=PHYSICIAN";
    }

    @GetMapping("/admin/physicians/{id}/profile")
    public String physicianProfile(@PathVariable Long id,
                                   @AuthenticationPrincipal ApplicationUserDetails user,
                                   Model model,
                                   RedirectAttributes flash) {
        try {
            PhysicianProfile profile = console.physicianProfile(id);
            model.addAttribute("user", user);
            model.addAttribute("role", "Administrator");
            model.addAttribute("profile", profile);
            model.addAttribute("physicianId", id);
            model.addAttribute("physicianUsername", profile.getUser().getUsername());
            return "admin/physician-profile";
        } catch (AccountValidationException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/accounts";
        }
    }

    @PostMapping("/admin/physicians/{id}/profile")
    public String updatePhysicianProfile(@PathVariable Long id,
                                         @RequestParam(required = false, defaultValue = "") String qualification,
                                         @RequestParam(required = false, defaultValue = "") String designation,
                                         @RequestParam(required = false, defaultValue = "") String department,
                                         @RequestParam(required = false, defaultValue = "") String organization,
                                         @RequestParam(required = false, defaultValue = "") String registrationNumber,
                                         @RequestParam(required = false, defaultValue = "") String email,
                                         @RequestParam(required = false, defaultValue = "") String phone,
                                         RedirectAttributes flash) {
        try {
            console.updatePhysicianProfile(id, qualification, designation, department,
                    organization, registrationNumber, email, phone);
            flash.addFlashAttribute("success", "Physician profile updated.");
        } catch (AccountValidationException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/physicians/" + id + "/profile";
    }

    // ─── Settings ─────────────────────────────────────────────────────

    @GetMapping("/admin/settings")
    public String settings(@AuthenticationPrincipal ApplicationUserDetails user, Model model) {
        List<SystemSetting> settings = console.settings();
        model.addAttribute("user", user);
        model.addAttribute("role", "Administrator");
        model.addAttribute("settings", settings);
        model.addAttribute("hasSettings", !settings.isEmpty());
        model.addAttribute("supportedLanguages", console.supportedLanguages());
        model.addAttribute("aiProviders", console.aiProviders());
        return "admin/settings";
    }

    @PostMapping("/admin/settings")
    public String saveSetting(@RequestParam String key,
                              @RequestParam String value,
                              @AuthenticationPrincipal ApplicationUserDetails actor,
                              RedirectAttributes flash) {
        try {
            console.saveSetting(key, value, actor == null ? null : actor.getId());
            flash.addFlashAttribute("success", "Setting saved: " + key);
        } catch (AccountValidationException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/settings";
    }

    @PostMapping("/admin/settings/delete")
    public String deleteSetting(@RequestParam String key,
                                @AuthenticationPrincipal ApplicationUserDetails actor,
                                RedirectAttributes flash) {
        console.deleteSetting(key, actor == null ? null : actor.getId());
        flash.addFlashAttribute("success", "Setting removed: " + key);
        return "redirect:/admin/settings";
    }
}
