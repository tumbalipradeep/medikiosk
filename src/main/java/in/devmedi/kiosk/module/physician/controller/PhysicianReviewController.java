package in.devmedi.kiosk.module.physician.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Redirects the old review-latest-case page to the new physician dashboard.
 *
 * <p>The previous {@code /physician/review} page showed only the latest
 * completed case. M5.2 replaces that with a full dashboard listing all cases,
 * each linked to its own per-case workspace page at
 * {@code /physician/cases/{caseId}}.</p>
 */
@Controller
public class PhysicianReviewController {

    @GetMapping("/physician/review")
    public String redirectToDashboard() {
        return "redirect:/physician/home";
    }
}