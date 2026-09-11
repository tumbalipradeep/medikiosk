package in.devmedi.kiosk.module.audit;

import in.devmedi.kiosk.module.audit.entity.AuditEvent;
import in.devmedi.kiosk.module.audit.entity.AuditEventType;
import in.devmedi.kiosk.module.audit.entity.AuditOutcome;
import in.devmedi.kiosk.module.audit.repository.AuditEventRepository;
import in.devmedi.kiosk.module.audit.service.AuditEventCommand;
import in.devmedi.kiosk.module.audit.service.AuditService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;

/**
 * Pure unit tests for {@link AuditService}. The service maps an immutable
 * command to the JPA entity, persists it in a {@code REQUIRES_NEW}
 * transaction, and swallows persistence failures at WARN level so audit
 * recording can never break a clinical workflow.
 */
class AuditServiceFailureUnitTest {

    private final AuditEventRepository auditEventRepository = mock(AuditEventRepository.class);
    private final AuditService auditService = new AuditService(auditEventRepository);

    @Test
    void recordPersistsEntityFromCommand() {
        AuditEventCommand command = AuditEventCommand.fhirExport("physician", "PHYSICIAN",
                "case-1", "/physician/cases/case-1/fhir", AuditOutcome.SUCCESS, null);

        auditService.record(command);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).saveAndFlush(captor.capture());
        AuditEvent event = captor.getValue();
        assertThat(event.getEventType()).isEqualTo(AuditEventType.FHIR_EXPORT);
        assertThat(event.getCaseId()).isEqualTo("case-1");
        assertThat(event.getActorUsername()).isEqualTo("physician");
        assertThat(event.getActorRole()).isEqualTo("PHYSICIAN");
        assertThat(event.getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(event.getOperation()).isEqualTo("EXPORT");
        assertThat(event.getResourceType()).isEqualTo("Bundle");
        assertThat(event.getFailureReason()).isNull();
        assertThat(event.getRequestPath()).isEqualTo("/physician/cases/case-1/fhir");
    }

    @Test
    void failureCommandPersistsFailureReason() {
        AuditEventCommand command = AuditEventCommand.fhirExport("physician", "PHYSICIAN",
                "does-not-exist", "/physician/cases/does-not-exist/fhir",
                AuditOutcome.FAILURE, "CASE_NOT_FOUND");

        auditService.record(command);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getOutcome()).isEqualTo(AuditOutcome.FAILURE);
        assertThat(captor.getValue().getFailureReason()).isEqualTo("CASE_NOT_FOUND");
    }

    @Test
    void persistenceExceptionDoesNotPropagate() {
        doThrow(new RuntimeException("database unavailable"))
                .when(auditEventRepository).saveAndFlush(any());

        AuditEventCommand command = AuditEventCommand.fhirExport("physician", "PHYSICIAN",
                "case-1", "/physician/cases/case-1/fhir", AuditOutcome.SUCCESS, null);

        assertThatCode(() -> auditService.record(command))
                .doesNotThrowAnyException();
    }

    @Test
    void nullActorFieldsAreAccepted() {
        AuditEventCommand command = new AuditEventCommand(
                AuditEventType.FHIR_EXPORT, Instant.now(),
                null, null, "case-1", "EXPORT", "Bundle",
                AuditOutcome.SUCCESS, null, "/physician/cases/case-1/fhir");

        auditService.record(command);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getActorUsername()).isNull();
        assertThat(captor.getValue().getActorRole()).isNull();
    }
}