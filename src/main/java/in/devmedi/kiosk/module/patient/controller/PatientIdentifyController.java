package in.devmedi.kiosk.module.patient.controller;

import in.devmedi.kiosk.module.auth.security.ApplicationUserDetails;
import in.devmedi.kiosk.module.patient.identity.PatientIdentityProvider;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Patient identity step of the kiosk journey.
 *
 * <p>Renders what identity the kiosk is operating under. In the current demo
 * deployment this is explicitly a local, non-ABHA identity - no ABHA number or
 * Aadhaar-derived identifier is ever fabricated or collected. The page is the
 * first honest boundary of the SIH26047 journey: it tells the patient plainly
 * that nothing will be linked to ABDM and that answers stay on the kiosk.</p>
 */
@Controller
@RequestMapping("/patient/identify")
public class PatientIdentifyController {

    public static final String IDENTITY_CONFIRMED_ATTRIBUTE = "patientIdentityConfirmed";

    private final PatientIdentityProvider identityProvider;

    public PatientIdentifyController(PatientIdentityProvider identityProvider) {
        this.identityProvider = identityProvider;
    }

    @GetMapping
    public String identify(@AuthenticationPrincipal ApplicationUserDetails user, Model model) {
        if (user == null) {
            return "redirect:/login";
        }
        model.addAttribute("user", user);
        model.addAttribute("identity", identityProvider.resolve(user));
        return "patient/identify";
    }

    @PostMapping("/confirm")
    public String confirm(@AuthenticationPrincipal ApplicationUserDetails user,
                          HttpSession session,
                          RedirectAttributes ra) {
        if (user == null) {
            return "redirect:/login";
        }
        session.setAttribute(IDENTITY_CONFIRMED_ATTRIBUTE, Boolean.TRUE);
        ra.addFlashAttribute("message", "Identity step completed. You can now choose your language and consent.");
        return "redirect:/patient/consent";
    }
}