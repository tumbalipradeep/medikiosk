package in.devmedi.kiosk.module.auth.controller;

import in.devmedi.kiosk.module.auth.security.SecurityPolicyProperties;
import in.devmedi.kiosk.module.auth.service.AccountLifecycleService;
import in.devmedi.kiosk.module.auth.service.AccountValidationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AuthController {

    private final AccountLifecycleService accountLifecycleService;
    private final SecurityPolicyProperties securityPolicyProperties;

    public AuthController(AccountLifecycleService accountLifecycleService,
                          SecurityPolicyProperties securityPolicyProperties) {
        this.accountLifecycleService = accountLifecycleService;
        this.securityPolicyProperties = securityPolicyProperties;
    }

    @GetMapping("/login")
    public String login() {
        return "auth/login";
    }

    @GetMapping("/register")
    public String register(Model model) {
        model.addAttribute("minPasswordLength", securityPolicyProperties.getMinPasswordLength());
        model.addAttribute("requireUppercase", securityPolicyProperties.isRequireUppercase());
        model.addAttribute("requireDigit", securityPolicyProperties.isRequireDigit());
        model.addAttribute("requireSpecial", securityPolicyProperties.isRequireSpecial());
        return "auth/register";
    }

    @PostMapping("/register")
    public String doRegister(@RequestParam String username,
                             @RequestParam String displayName,
                             @RequestParam String password,
                             @RequestParam String confirmPassword,
                             Model model) {
        String redirect = "auth/register";
        if (password != null && !password.equals(confirmPassword)) {
            model.addAttribute("error", "Passwords do not match.");
        } else {
            try {
                accountLifecycleService.registerPatient(username, password, displayName);
                return "redirect:/login?registered";
            } catch (AccountValidationException ex) {
                model.addAttribute("error", ex.getMessage());
            }
        }
        model.addAttribute("username", username);
        model.addAttribute("displayName", displayName);
        model.addAttribute("minPasswordLength", securityPolicyProperties.getMinPasswordLength());
        model.addAttribute("requireUppercase", securityPolicyProperties.isRequireUppercase());
        model.addAttribute("requireDigit", securityPolicyProperties.isRequireDigit());
        model.addAttribute("requireSpecial", securityPolicyProperties.isRequireSpecial());
        return redirect;
    }
}