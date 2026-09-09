package in.devmedi.kiosk.home;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String home(Authentication authentication) {
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
        return "home";
    }
}