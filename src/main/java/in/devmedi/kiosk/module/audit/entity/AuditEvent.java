package in.devmedi.kiosk.module.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private AuditEventType eventType;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "actor_username", length = 100)
    private String actorUsername;

    @Column(name = "actor_role", length = 16)
    private String actorRole;

    @Column(name = "case_id", length = 64)
    private String caseId;

    @Column(nullable = false, length = 32)
    private String operation;

    @Column(name = "resource_type", nullable = false, length = 32)
    private String resourceType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AuditOutcome outcome;

    @Column(name = "failure_reason", length = 128)
    private String failureReason;

    @Column(name = "request_path", length = 255)
    private String requestPath;

    protected AuditEvent() {
    }

    public AuditEvent(AuditEventType eventType,
                      Instant occurredAt,
                      String actorUsername,
                      String actorRole,
                      String caseId,
                      String operation,
                      String resourceType,
                      AuditOutcome outcome,
                      String failureReason,
                      String requestPath) {
        this.eventType = eventType;
        this.occurredAt = occurredAt;
        this.actorUsername = actorUsername;
        this.actorRole = actorRole;
        this.caseId = caseId;
        this.operation = operation;
        this.resourceType = resourceType;
        this.outcome = outcome;
        this.failureReason = failureReason;
        this.requestPath = requestPath;
    }

    @PrePersist
    protected void ensureOccurredAt() {
        if (this.occurredAt == null) {
            this.occurredAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public AuditEventType getEventType() {
        return eventType;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getActorUsername() {
        return actorUsername;
    }

    public String getActorRole() {
        return actorRole;
    }

    public String getCaseId() {
        return caseId;
    }

    public String getOperation() {
        return operation;
    }

    public String getResourceType() {
        return resourceType;
    }

    public AuditOutcome getOutcome() {
        return outcome;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public String getRequestPath() {
        return requestPath;
    }
}