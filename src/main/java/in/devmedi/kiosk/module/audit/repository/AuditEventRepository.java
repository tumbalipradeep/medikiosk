package in.devmedi.kiosk.module.audit.repository;

import in.devmedi.kiosk.module.audit.entity.AuditEvent;
import in.devmedi.kiosk.module.audit.entity.AuditEventType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Persistence for the minimal audit trail. Applications code never depends on
 * this repository directly; {@code AuditService} is the only entry point for
 * recording events. Read-only query helpers exist for security review and for
 * tests asserting what was (and was not) recorded.
 */
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    List<AuditEvent> findByCaseIdOrderByOccurredAtAsc(String caseId);

    long countByEventType(AuditEventType eventType);
}