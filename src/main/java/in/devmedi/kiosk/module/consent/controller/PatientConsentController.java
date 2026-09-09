package in.devmedi.kiosk.module.consent.controller;

import in.devmedi.kiosk.module.auth.security.ApplicationUserDetails;
import in.devmedi.kiosk.module.consent.entity.ConsentState;
import in.devmedi.kiosk.module.consent.entity.ConsentType;
import in.devmedi.kiosk.module.consent.service.ConsentService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Controller
@RequestMapping("/patient/consent")
public class PatientConsentController {

    private final ConsentService consentService;

    public PatientConsentController(ConsentService consentService) {
        this.consentService = consentService;
    }

    @GetMapping
    public String consentPage(@AuthenticationPrincipal ApplicationUserDetails user, Model model) {
        if (user == null) {
            return "redirect:/login";
        }
        Map<ConsentType, Boolean> granted = currentGranted(user.getId());
        model.addAttribute("user", user);
        model.addAttribute("granted", granted);
        model.addAttribute("consents", buildPurposeList(granted));
        return "patient/consent";
    }

    @PostMapping("/grant")
    public String grant(@AuthenticationPrincipal ApplicationUserDetails user,
                        @RequestParam("type") ConsentType type,
                        RedirectAttributes ra) {
        if (user == null) {
            return "redirect:/login";
        }
        consentService.grant(user.getId(), type);
        ra.addFlashAttribute("message", "Consent granted: " + type.readableName());
        return "redirect:/patient/consent";
    }

    @PostMapping("/revoke")
    public String revoke(@AuthenticationPrincipal ApplicationUserDetails user,
                         @RequestParam("type") ConsentType type,
                         RedirectAttributes ra) {
        if (user == null) {
            return "redirect:/login";
        }
        consentService.revoke(user.getId(), type);
        ra.addFlashAttribute("message", "Consent revoked: " + type.readableName());
        return "redirect:/patient/consent";
    }

    private Map<ConsentType, Boolean> currentGranted(Long userId) {
        Map<ConsentType, Boolean> granted = new EnumMap<>(ConsentType.class);
        consentService.currentStates(userId).forEach((type, state) ->
                granted.put(type, state == ConsentState.GRANTED));
        return granted;
    }

    private Map<ConsentType, Map<String, Object>> buildPurposeList(Map<ConsentType, Boolean> granted) {
        Map<ConsentType, Map<String, Object>> items = new LinkedHashMap<>();
        for (ConsentType type : ConsentType.values()) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("name", type.readableName());
            e.put("purpose", consentService.purpose(type));
            e.put("granted", granted.getOrDefault(type, false));
            items.put(type, e);
        }
        return items;
    }
}
