package in.devmedi.kiosk.module.audit.service;

import in.devmedi.kiosk.module.audit.entity.AuditEvent;
import in.devmedi.kiosk.module.audit.entity.AuditEventType;
import in.devmedi.kiosk.module.audit.entity.AuditOutcome;
import in.devmedi.kiosk.module.audit.repository.AuditEventRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Read-only querying of the audit trail for the admin control center.
 *
 * <p>Filters are optional and composable: event type, actor (case-insensitive
 * contains), outcome, and case id. Results are always newest-first and paged;
 * page size is clamped so a malicious or mistaken request cannot pull the
 * whole trail at once.</p>
 */
@Service
public class AuditQueryService {

    private static final List<Integer> ALLOWED_PAGE_SIZES = List.of(10, 25, 50, 100);
    private static final int DEFAULT_PAGE_SIZE = 25;

    private final AuditEventRepository auditEventRepository;

    public AuditQueryService(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @Transactional(readOnly = true)
    public Page<AuditEvent> search(AuditEventType eventType,
                                   String actor,
                                   AuditOutcome outcome,
                                   String caseId,
                                   int page,
                                   Integer size) {
        int pageSize = normalizePageSize(size);
        int pageIndex = Math.max(0, page);
        Pageable pageable = PageRequest.of(pageIndex, pageSize,
                Sort.by(Sort.Direction.DESC, "occurredAt").and(Sort.by(Sort.Direction.DESC, "id")));

        Specification<AuditEvent> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (eventType != null) {
                predicates.add(cb.equal(root.get("eventType"), eventType));
            }
            if (actor != null && !actor.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("actorUsername")),
                        "%" + actor.trim().toLowerCase(Locale.ROOT) + "%"));
            }
            if (outcome != null) {
                predicates.add(cb.equal(root.get("outcome"), outcome));
            }
            if (caseId != null && !caseId.isBlank()) {
                predicates.add(cb.equal(root.get("caseId"), caseId.trim()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return auditEventRepository.findAll(spec, pageable);
    }

    /** Clamps the requested page size to the operationally safe set. */
    private int normalizePageSize(Integer size) {
        if (size == null) {
            return DEFAULT_PAGE_SIZE;
        }
        return ALLOWED_PAGE_SIZES.stream()
                .filter(s -> s == size)
                .findFirst()
                .orElse(DEFAULT_PAGE_SIZE);
    }

    /** Row shape for the audit view, mirroring the console's AuditEventRow. */
    public record AuditRow(Long id,
                           java.time.Instant occurredAt,
                           String eventType,
                           String actorUsername,
                           String actorRole,
                           String operation,
                           String resourceType,
                           String outcome,
                           String caseId,
                           String failureReason) {

        public static AuditRow from(AuditEvent e) {
            return new AuditRow(e.getId(), e.getOccurredAt(), e.getEventType().name(),
                    e.getActorUsername(), e.getActorRole(), e.getOperation(),
                    e.getResourceType(), e.getOutcome().name(), e.getCaseId(),
                    e.getFailureReason());
        }
    }
}
