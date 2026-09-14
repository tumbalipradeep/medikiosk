package in.devmedi.kiosk.module.patient.controller;

import in.devmedi.kiosk.module.auth.security.ApplicationUserDetails;
import in.devmedi.kiosk.module.clinical.history.ClinicalHistoryService;
import in.devmedi.kiosk.module.clinical.history.ClinicalHistoryCategory;
import in.devmedi.kiosk.module.clinical.history.ClinicalHistoryItem;
import in.devmedi.kiosk.module.encounter.Encounter;
import in.devmedi.kiosk.module.encounter.EncounterService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;
import java.util.Map;

/**
 * Patient-facing view of their own complete clinical history.
 *
 * <p>Every item is shown with its provenance and category, and the page
 * presents only history belonging to the signed-in patient.</p>
 */
@Controller
@RequestMapping("/patient/history")
public class PatientClinicalHistoryController {

    private final ClinicalHistoryService clinicalHistoryService;
    private final EncounterService encounterService;

    public PatientClinicalHistoryController(ClinicalHistoryService clinicalHistoryService,
                                            EncounterService encounterService) {
        this.clinicalHistoryService = clinicalHistoryService;
        this.encounterService = encounterService;
    }

    @GetMapping
    public String history(@AuthenticationPrincipal ApplicationUserDetails user, Model model) {
        if (user == null) {
            return "redirect:/login";
        }
        Long patientId = user.getId();
        Map<ClinicalHistoryCategory, List<ClinicalHistoryItem>> grouped =
                clinicalHistoryService.groupedFor(patientId);
        Encounter latest = encounterService.latestSubmitted(patientId).orElse(null);
        model.addAttribute("user", user);
        model.addAttribute("categories", grouped);
        model.addAttribute("totalItems", clinicalHistoryService.countFor(patientId));
        model.addAttribute("hasHistory", !grouped.isEmpty());
        model.addAttribute("latestEncounterStatus", latest == null ? null : latest.getStatus());
        return "patient/history";
    }
}