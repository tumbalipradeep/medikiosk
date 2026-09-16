package in.devmedi.kiosk.module.admin.controller;

import in.devmedi.kiosk.module.audit.entity.AuditEventType;
import in.devmedi.kiosk.module.audit.entity.AuditOutcome;
import in.devmedi.kiosk.module.audit.service.AuditQueryService;
import in.devmedi.kiosk.module.auth.security.ApplicationUserDetails;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Admin audit trail view: filterable (event type, actor, outcome, case) and
 * paginated. Read-only — the control center inspects the audit trail but never
 * edits it.
 */
@Controller
@RequestMapping("/admin/audit")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAuditController {

    private final AuditQueryService auditQueryService;

    public AdminAuditController(AuditQueryService auditQueryService) {
        this.auditQueryService = auditQueryService;
    }

    @GetMapping
    public String audit(@RequestParam(name = "eventType", required = false) String eventType,
                        @RequestParam(name = "actor", required = false) String actor,
                        @RequestParam(name = "outcome", required = false) String outcome,
                        @RequestParam(name = "caseId", required = false) String caseId,
                        @RequestParam(name = "page", defaultValue = "0") int page,
                        @RequestParam(name = "size", required = false) Integer size,
                        @AuthenticationPrincipal ApplicationUserDetails user,
                        Model model) {
        AuditEventType typeFilter = parseEventType(eventType);
        AuditOutcome outcomeFilter = parseOutcome(outcome);

        Page<AuditQueryService.AuditRow> rows = auditQueryService
                .search(typeFilter, actor, outcomeFilter, caseId, page, size)
                .map(AuditQueryService.AuditRow::from);

        model.addAttribute("user", user);
        model.addAttribute("role", "Administrator");
        model.addAttribute("auditPage", rows);
        model.addAttribute("events", AuditEventType.values());
        model.addAttribute("outcomes", AuditOutcome.values());
        model.addAttribute("eventTypeFilter", typeFilter == null ? "" : typeFilter.name());
        model.addAttribute("actorFilter", actor == null ? "" : actor);
        model.addAttribute("outcomeFilter", outcomeFilter == null ? "" : outcomeFilter.name());
        model.addAttribute("caseIdFilter", caseId == null ? "" : caseId);
        model.addAttribute("pageSize", size == null ? "" : String.valueOf(size));
        model.addAttribute("hasEvents", rows.hasContent());
        return "admin/audit";
    }

    private static AuditEventType parseEventType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return AuditEventType.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static AuditOutcome parseOutcome(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return AuditOutcome.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
        }
        return null;
    }
}
