package in.devmedi.kiosk.module.patient.controller;

import in.devmedi.kiosk.module.auth.security.ApplicationUserDetails;
import in.devmedi.kiosk.module.patient.identity.PatientIdentityProvider;
import in.devmedi.kiosk.module.physician.service.CompletedCasePersistenceService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Optional;

@Controller
public class PatientHomeController {

    private final PatientIdentityProvider identityProvider;
    private final CompletedCasePersistenceService casePersistence;

    public PatientHomeController(PatientIdentityProvider identityProvider,
                                 CompletedCasePersistenceService casePersistence) {
        this.identityProvider = identityProvider;
        this.casePersistence = casePersistence;
    }

    @GetMapping("/patient/home")
    public String home(@AuthenticationPrincipal ApplicationUserDetails user, Model model) {
        model.addAttribute("user", user);
        model.addAttribute("role", "Patient");
        model.addAttribute("identity", identityProvider.resolve(user));
        Optional<String> latestCaseId = user == null
                ? Optional.empty()
                : casePersistence.findLatestByUser(user.getId()).map(c -> c.id());
        model.addAttribute("latestCaseId", latestCaseId.orElse(null));
        return "patient/home";
    }
}