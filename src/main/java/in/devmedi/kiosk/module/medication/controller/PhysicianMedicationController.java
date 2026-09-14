package in.devmedi.kiosk.module.medication.controller;

import in.devmedi.kiosk.module.auth.security.ApplicationUserDetails;
import in.devmedi.kiosk.module.medication.service.MedicationInteractionReport;
import in.devmedi.kiosk.module.medication.service.MedicationInteractionService;
import in.devmedi.kiosk.module.physician.assignment.CaseAccessDeniedException;
import in.devmedi.kiosk.module.physician.assignment.CaseAssignmentService;
import in.devmedi.kiosk.module.physician.workspace.CaseNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Medication interaction screening for a physician case. Routes live under
 * {@code /physician/**} so {@code ROLE_PHYSICIAN} is enforced by the existing
 * {@code SecurityConfig} path rule. Responses are the assembled screening
 * report; the physician workspace page renders it.
 *
 * <p>This is a screening aid only — it surfaces known pairings for human
 * review and never prescribes, doses, or diagnoses.</p>
 */
@RestController
public class PhysicianMedicationController {

    private final MedicationInteractionService service;
    private final CaseAssignmentService assignmentService;

    public PhysicianMedicationController(MedicationInteractionService service,
                                         CaseAssignmentService assignmentService) {
        this.service = service;
        this.assignmentService = assignmentService;
    }

    @GetMapping("/physician/cases/{caseId}/medications")
    public MedicationInteractionReport report(@PathVariable String caseId,
                                              @AuthenticationPrincipal ApplicationUserDetails actor) {
        assignmentService.requireAccess(caseId, actor == null ? null : actor.getId());
        return service.report(caseId);
    }

    @PostMapping("/physician/cases/{caseId}/medications")
    public MedicationInteractionReport add(@PathVariable String caseId,
                                           @RequestBody Map<String, String> body,
                                           @AuthenticationPrincipal ApplicationUserDetails actor) {
        assignmentService.requireAccess(caseId, actor == null ? null : actor.getId());
        Long physicianId = actor == null ? null : actor.getId();
        return service.add(caseId,
                body.getOrDefault("name", ""),
                body.getOrDefault("dose", ""),
                body.getOrDefault("frequency", ""),
                physicianId);
    }

    @PostMapping("/physician/cases/{caseId}/medications/{name}/suppress")
    public MedicationInteractionReport suppress(@PathVariable String caseId,
                                                @PathVariable String name,
                                                @AuthenticationPrincipal ApplicationUserDetails actor) {
        assignmentService.requireAccess(caseId, actor == null ? null : actor.getId());
        Long physicianId = actor == null ? null : actor.getId();
        return service.suppress(caseId, name, physicianId);
    }

    @PostMapping("/physician/cases/{caseId}/medications/{name}/restore")
    public MedicationInteractionReport restore(@PathVariable String caseId,
                                               @PathVariable String name,
                                               @AuthenticationPrincipal ApplicationUserDetails actor) {
        assignmentService.requireAccess(caseId, actor == null ? null : actor.getId());
        Long physicianId = actor == null ? null : actor.getId();
        return service.restore(caseId, name, physicianId);
    }

    @ExceptionHandler(CaseNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(CaseNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(CaseAccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(CaseAccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "Bad request", "message", ex.getMessage() == null ? "Invalid input" : ex.getMessage()));
    }
}