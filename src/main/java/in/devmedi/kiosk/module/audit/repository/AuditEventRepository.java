package in.devmedi.kiosk.module.audit.repository;

import in.devmedi.kiosk.module.audit.entity.AuditEvent;
import in.devmedi.kiosk.module.audit.entity.AuditEventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

/**
 * Persistence for the minimal audit trail. Applications code never depends on
 * this repository directly; {@code AuditService} is the only entry point for
 * recording events. Read-only query helpers exist for security review and for
 * tests asserting what was (and was not) recorded.
 */
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long>, JpaSpecificationExecutor<AuditEvent> {

    List<AuditEvent> findByCaseIdOrderByOccurredAtAsc(String caseId);

    List<AuditEvent> findByCaseIdOrderByOccurredAtDesc(String caseId);

    List<AuditEvent> findTop10ByOrderByOccurredAtDesc();

    long countByEventType(AuditEventType eventType);
}