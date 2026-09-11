package in.devmedi.kiosk.module.audit.service;

import in.devmedi.kiosk.module.audit.entity.AuditEvent;
import in.devmedi.kiosk.module.audit.repository.AuditEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Small audit-recording abstraction so application code never touches the JPA
 * repository directly. Consume via {@link #record(AuditEventCommand)}; the
 * command carries only the identifiers required for security review.
 *
 * <p><strong>Transaction semantics.</strong> Recording runs in its own
 * {@code REQUIRES_NEW} transaction so the audit write never joins the
 * read-only clinical export transaction ({@code @Transactional(readOnly = true)}
 * on {@code FhirCaseExportService}). A failed audit insert rolls back only the
 * audit record - it can never corrupt, roll back, or commit clinical data, and
 * it can never fail the export request that triggered it.</p>
 *
 * <p><strong>Failure semantics.</strong> Persistence failures are logged at
 * WARN with the full exception (never silently swallowed) and are not
 * rethrown: audit is observational and must not break clinical workflows.
 * A future ABDM adapter with a mandatory-compliance audit requirement may add
 * its own explicit failure policy on top of this service; it would not change
 * this behavior.</p>
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditEventRepository auditEventRepository;

    public AuditService(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditEventCommand command) {
        try {
            auditEventRepository.saveAndFlush(AuditEventCommandMapper.toEntity(command));
        } catch (RuntimeException ex) {
            log.warn("audit.record.failed eventType={} caseId={} outcome={}",
                    command.eventType(), command.caseId(), command.outcome(), ex);
        }
    }

    /** Maps the immutable command to the persistent entity. */
    private static final class AuditEventCommandMapper {
        private static AuditEvent toEntity(AuditEventCommand command) {
            return new AuditEvent(command.eventType(), command.occurredAt(), command.actorUsername(),
                    command.actorRole(), command.caseId(), command.operation(), command.resourceType(),
                    command.outcome(), command.failureReason(), command.requestPath());
        }
    }
}