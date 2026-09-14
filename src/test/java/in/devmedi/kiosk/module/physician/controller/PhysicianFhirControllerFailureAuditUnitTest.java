package in.devmedi.kiosk.module.physician.controller;

import in.devmedi.kiosk.module.audit.entity.AuditOutcome;
import in.devmedi.kiosk.module.audit.service.AuditEventCommand;
import in.devmedi.kiosk.module.audit.service.AuditService;
import in.devmedi.kiosk.module.auth.entity.Role;
import in.devmedi.kiosk.module.auth.entity.User;
import in.devmedi.kiosk.module.auth.security.ApplicationUserDetails;
import in.devmedi.kiosk.module.fhir.interop.FhirExportContract;
import in.devmedi.kiosk.module.fhir.interop.FhirExportTransport;
import in.devmedi.kiosk.module.fhir.model.FhirBundle;
import in.devmedi.kiosk.module.fhir.service.FhirCaseExportService;
import in.devmedi.kiosk.module.physician.assignment.CaseAssignmentService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the FHIR export failure-audit boundary.
 *
 * <p>When the export service or transport fails with an unexpected runtime
 * error, {@link PhysicianFhirController} must record a FAILURE/INTERNAL_ERROR
 * audit event before rethrowing, so an observable export failure never goes
 * unseen in the audit trail. Security-filter-level outcomes (403/redirect) are
 * not the controller's responsibility and are deliberately not exercised here.</p>
 */
@ExtendWith(MockitoExtension.class)
class PhysicianFhirControllerFailureAuditUnitTest {

    @Mock
    private FhirCaseExportService exportService;

    @Mock
    private AuditService auditService;

    @Mock
    private FhirExportTransport transport;

    @Mock
    private CaseAssignmentService assignmentService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private FhirBundle bundle;

    private PhysicianFhirController controller;

    private ApplicationUserDetails actor;

    @BeforeEach
    void setUp() {
        controller = new PhysicianFhirController(exportService, auditService, transport, assignmentService);
        actor = new ApplicationUserDetails(
                new User("physician", "irrelevant", "Dr. Physician", Role.PHYSICIAN));
        when(request.getRequestURI()).thenReturn("/physician/cases/case-a/fhir");
    }

    @Test
    void exportFailureIsAuditedAsInternalErrorBeforeRethrowing() {
        when(exportService.exportCompletedCase("case-a")).thenThrow(
                new IllegalStateException("bundle generation exploded"));

        assertThatThrownBy(() -> controller.fhir("case-a", actor, request))
                .isInstanceOf(IllegalStateException.class);

        verify(auditService).record(argThat(command -> command.outcome() == AuditOutcome.FAILURE
                && "INTERNAL_ERROR".equals(command.failureReason())
                && "PHYSICIAN".equals(command.actorRole())
                && "physician".equals(command.actorUsername())
                && "case-a".equals(command.caseId())
                && "EXPORT".equals(command.operation())
                && "Bundle".equals(command.resourceType())
                && "/physician/cases/case-a/fhir".equals(command.requestPath())));
    }

    @Test
    void transportFailureIsAuditedAsInternalErrorBeforeRethrowing() {
        when(exportService.exportCompletedCase("case-a")).thenReturn(Optional.of(bundle));
        doThrow(new IllegalStateException("transport exploded")).when(transport).transmit(any());

        assertThatThrownBy(() -> controller.fhir("case-a", actor, request))
                .isInstanceOf(IllegalStateException.class);

        verify(auditService).record(argThat(command -> command.outcome() == AuditOutcome.FAILURE
                && "INTERNAL_ERROR".equals(command.failureReason())));
        verify(auditService, never()).record(argThat(command -> command.outcome() == AuditOutcome.SUCCESS));
    }

    @Test
    void caseNotFoundIsAuditedAsResolvableFailureAndNothingIsTransmitted() {
        when(exportService.exportCompletedCase("pony")).thenReturn(Optional.empty());
        when(request.getRequestURI()).thenReturn("/physician/cases/pony/fhir");

        assertThatThrownBy(() -> controller.fhir("pony", actor, request))
                .isInstanceOf(IllegalArgumentException.class);

        verify(auditService).record(argThat(command -> command.outcome() == AuditOutcome.FAILURE
                && "CASE_NOT_FOUND".equals(command.failureReason())));
        verify(transport, never()).transmit(any());
    }
}