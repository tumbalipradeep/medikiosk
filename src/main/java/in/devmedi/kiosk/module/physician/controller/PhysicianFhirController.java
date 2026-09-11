package in.devmedi.kiosk.module.physician.controller;

import in.devmedi.kiosk.module.audit.entity.AuditOutcome;
import in.devmedi.kiosk.module.audit.service.AuditEventCommand;
import in.devmedi.kiosk.module.audit.service.AuditService;
import in.devmedi.kiosk.module.auth.security.ApplicationUserDetails;
import in.devmedi.kiosk.module.fhir.interop.FhirExportContract;
import in.devmedi.kiosk.module.fhir.interop.FhirExportTransport;
import in.devmedi.kiosk.module.fhir.model.FhirBundle;
import in.devmedi.kiosk.module.fhir.service.FhirCaseExportService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

/**
 * Physician-only FHIR R4 export endpoint with audit trail and ABDM-ready
 * interoperability boundary.
 *
 * <p>Exposes the deterministic collection {@code Bundle} produced by
 * {@link FhirCaseExportService} for one completed case. The route lives under
 * {@code /physician/**} so {@code ROLE_PHYSICIAN} is required by the existing
 * {@code SecurityConfig} path rule - the same access rule the timeline,
 * findings and extraction endpoints already use. As in the rest of the
 * physician workflow there is no per-physician case assignment in the MediKiosk
 * model, so a case that does not exist (or cannot be resolved) maps to 404 in
 * the established convention; this endpoint introduces no new permissions,
 * roles, or authorization mechanism.</p>
 *
 * <p>On every controller-observable outcome (success or resolvable-case
 * failure) a minimal audit event is recorded via {@link AuditService} in its
 * own {@code REQUIRES_NEW} transaction so the write can never corrupt, roll
 * back, or fail the clinical export request. Audit failures are logged but
 * never propagated. Only controller-observable outcomes are audited: 403
 * responses from Spring Security filters and anonymous redirects are handled
 * upstream and deliberately not recorded here - the application does not
 * bypass security filters to create audit events.</p>
 *
 * <p>After a successful export the generated Bundle is passed through
 * {@link FhirExportTransport} so the bundle reaches the interoperability
 * boundary. The current {@code LocalOnlyExportTransport} does not transmit
 * anything - no network call, no credential lookup, no fake ABDM response.
 * A future ABDM adapter replaces that bean.</p>
 *
 * <p>The response is served as {@code application/fhir+json} and carries an
 * {@code X-Fhir-Version} header declaring the generation target
 * ({@code 4.0.1} FHIR R4). This does not claim profile-level conformance.
 * The body is byte-for-byte the deterministic output of the export service
 * (stable ids, timestamp derived from the persisted case creation time, no
 * binary document content, no secrets, no database identity values).</p>
 */
@RestController
@RequestMapping("/physician/cases/{caseId}")
public class PhysicianFhirController {

    private static final String FHIR_JSON = "application/fhir+json";

    private final FhirCaseExportService fhirCaseExportService;
    private final AuditService auditService;
    private final FhirExportTransport fhirExportTransport;

    public PhysicianFhirController(FhirCaseExportService fhirCaseExportService,
                                   AuditService auditService,
                                   FhirExportTransport fhirExportTransport) {
        this.fhirCaseExportService = fhirCaseExportService;
        this.auditService = auditService;
        this.fhirExportTransport = fhirExportTransport;
    }

    @GetMapping(value = "/fhir", produces = FHIR_JSON)
    public ResponseEntity<FhirBundle> fhir(@PathVariable String caseId,
                                           @AuthenticationPrincipal ApplicationUserDetails actor,
                                           HttpServletRequest request) {
        String username = actor == null ? null : actor.getUsername();
        String role = actor == null ? null : stripRolePrefix(actor);
        String requestPath = request.getRequestURI();

        Optional<FhirBundle> maybe = fhirCaseExportService.exportCompletedCase(caseId);
        if (maybe.isEmpty()) {
            auditService.record(AuditEventCommand.fhirExport(username, role, caseId, requestPath,
                    AuditOutcome.FAILURE, "CASE_NOT_FOUND"));
            throw new IllegalArgumentException("Case not found");
        }

        FhirBundle bundle = maybe.get();
        FhirExportContract contract = FhirExportContract.of(caseId, bundle);
        fhirExportTransport.transmit(contract);
        auditService.record(AuditEventCommand.fhirExport(username, role, caseId, requestPath,
                AuditOutcome.SUCCESS, null));

        return ResponseEntity.ok()
                .header(FhirExportContract.FHIR_VERSION_HEADER, FhirExportContract.FHIR_VERSION)
                .body(bundle);
    }

    /** Derive the role label from the single granted authority (ROLE_ prefix). */
    private static String stripRolePrefix(ApplicationUserDetails actor) {
        return actor.getAuthorities().stream()
                .findFirst()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.startsWith("ROLE_") ? a.substring(5) : a)
                .orElse(null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }
}