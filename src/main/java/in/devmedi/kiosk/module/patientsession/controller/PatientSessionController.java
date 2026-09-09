package in.devmedi.kiosk.module.patientsession.controller;

import in.devmedi.kiosk.module.auth.security.ApplicationUserDetails;
import in.devmedi.kiosk.module.patientsession.service.PatientSessionService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/patient")
public class PatientSessionController {

    private final PatientSessionService patientSessionService;

    public PatientSessionController(PatientSessionService patientSessionService) {
        this.patientSessionService = patientSessionService;
    }

    @PostMapping("/session/start")
    public String startSession(@AuthenticationPrincipal ApplicationUserDetails user,
                               RedirectAttributes ra) {
        if (user == null) {
            return "redirect:/login";
        }
        try {
            patientSessionService.start(user.getId());
        } catch (IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/patient/consent";
        }
        ra.addFlashAttribute("message", "Patient session started.");
        return "redirect:/patient/intake";
    }

    @GetMapping("/intake")
    public String intake(@AuthenticationPrincipal ApplicationUserDetails user, Model model) {
        model.addAttribute("user", user);
        return "patient/intake";
    }
}
