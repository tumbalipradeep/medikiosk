package in.devmedi.kiosk.home;

import in.devmedi.kiosk.module.admin.config.SystemSetting;
import in.devmedi.kiosk.module.admin.config.SystemSettingRepository;
import in.devmedi.kiosk.module.ai.provider.AiFailoverService;
import in.devmedi.kiosk.module.ai.provider.AiFailoverService.AiProviderEnabled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * Public web surface. The site's informational pages ({@code /about},
 * {@code /features}, {@code /privacy}, {@code /contact}) are normal website
 * pages; {@code /contact} is the only one with a backend dependency &mdash; it
 * reads the operator-configured support address from the runtime settings
 * store and renders it read-only.
 */
@Controller
public class HomeController {

    private final SystemSettingRepository settingRepository;
    private final AiFailoverService aiFailoverService;

    public HomeController(SystemSettingRepository settingRepository,
                          AiFailoverService aiFailoverService) {
        this.settingRepository = settingRepository;
        this.aiFailoverService = aiFailoverService;
    }

    @GetMapping("/")
    public String home(Authentication authentication, Model model) {
        if (authentication != null && authentication.isAuthenticated()) {
            for (GrantedAuthority authority : authentication.getAuthorities()) {
                switch (authority.getAuthority()) {
                    case "ROLE_ADMIN" -> {
                        return "redirect:/admin/home";
                    }
                    case "ROLE_PHYSICIAN" -> {
                        return "redirect:/physician/home";
                    }
                    case "ROLE_PATIENT" -> {
                        return "redirect:/patient/home";
                    }
                    default -> {
                    }
                }
            }
        }
        // The landing AI badge is derived from the same authoritative source the
        // runtime uses to decide whether any conversational-AI provider will be
        // attempted (failover order + API-key presence). No hard-coded claim.
        List<AiProviderEnabled> aiProviders = aiFailoverService.providerStatuses();
        model.addAttribute("aiProviders", aiProviders);
        model.addAttribute("aiConfigured",
                aiProviders.stream().anyMatch(AiProviderEnabled::enabled));
        return "home";
    }

    @GetMapping("/about")
    public String about() {
        return "about";
    }

    @GetMapping("/features")
    public String features() {
        return "features";
    }

    @GetMapping("/privacy")
    public String privacy() {
        return "privacy";
    }

    @GetMapping("/contact")
    public String contact(Model model) {
        String supportEmail = settingRepository.findByKey("kiosk.support_email")
                .map(SystemSetting::getValue)
                .orElse(null);
        model.addAttribute("supportEmail", supportEmail);
        return "contact";
    }
}