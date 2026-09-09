package in.devmedi.kiosk.module.physician.controller;

import in.devmedi.kiosk.module.auth.security.ApplicationUserDetails;
import in.devmedi.kiosk.module.clinical.summary.ClinicalSummary;
import in.devmedi.kiosk.module.physician.service.DemoPhysicianReviewService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PhysicianReviewController {

    private final DemoPhysicianReviewService demoReviewService;

    public PhysicianReviewController(DemoPhysicianReviewService demoReviewService) {
        this.demoReviewService = demoReviewService;
    }

    @GetMapping("/physician/review")
    public String review(@AuthenticationPrincipal ApplicationUserDetails user, Model model) {
        ClinicalSummary summary = demoReviewService.demoSummary();
        model.addAttribute("user", user);
        model.addAttribute("role", "Physician");
        model.addAttribute("sections", summary.sections());
        model.addAttribute("answeredCount", summary.answeredCount());
        return "physician/review";
    }
}