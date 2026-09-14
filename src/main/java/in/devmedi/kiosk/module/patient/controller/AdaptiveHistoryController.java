package in.devmedi.kiosk.module.patient.controller;

import in.devmedi.kiosk.module.auth.security.ApplicationUserDetails;
import in.devmedi.kiosk.module.clinical.adaptive.AdaptiveConversationService;
import in.devmedi.kiosk.module.clinical.adaptive.AdaptiveView;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

/**
 * Patient-facing adaptive complete-clinical-history conversation.
 *
 * <p>Serves the chat-style page and the JSON state/answer endpoints. Only the
 * signed-in patient's own history is ever touched.</p>
 */
@Controller
@RequestMapping("/patient/history/adaptive")
public class AdaptiveHistoryController {

    private final AdaptiveConversationService service;

    public AdaptiveHistoryController(AdaptiveConversationService service) {
        this.service = service;
    }

    @GetMapping
    public String page(@AuthenticationPrincipal ApplicationUserDetails user, Model model) {
        if (user == null) {
            return "redirect:/login";
        }
        model.addAttribute("user", user);
        return "patient/adaptive-history";
    }

    @PostMapping("/start")
    @ResponseBody
    public ResponseEntity<AdaptiveView> start(@AuthenticationPrincipal ApplicationUserDetails user) {
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(service.start(user.getId()));
    }

    @PostMapping("/answer")
    @ResponseBody
    public ResponseEntity<AdaptiveView> answer(@AuthenticationPrincipal ApplicationUserDetails user,
                                               @RequestBody Map<String, String> body) {
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        String questionId = body.get("questionId");
        String answer = body.get("answer");
        if (questionId == null || questionId.isBlank() || answer == null || answer.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            return ResponseEntity.ok(service.answer(user.getId(), questionId, answer));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(service.current(user.getId()));
        }
    }

    @GetMapping("/state")
    @ResponseBody
    public ResponseEntity<AdaptiveView> state(@AuthenticationPrincipal ApplicationUserDetails user) {
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(service.current(user.getId()));
    }
}