package in.devmedi.kiosk.module.admin.controller;

import in.devmedi.kiosk.module.auth.security.ApplicationUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminHomeController {

    @GetMapping("/admin/home")
    public String home(@AuthenticationPrincipal ApplicationUserDetails user, Model model) {
        model.addAttribute("user", user);
        model.addAttribute("role", "Administrator");
        return "admin/home";
    }
}