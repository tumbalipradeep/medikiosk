package in.devmedi.kiosk.module.physician.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A persisted completed patient intake case, keyed by its stable case identity.
 *
 * <p>The stable {@code caseId} is the same identity exposed to the physician
 * review flow, so a case survives application restarts as long as it lives in
 * the database. Its captured answers are held separately, in order, in
 * {@link CompletedCaseAnswerEntity}.</p>
 */
@Entity
@Table(name = "completed_cases")
public class CompletedCaseEntity {

    @Id
    @Column(name = "case_id", nullable = false, length = 64)
    private String caseId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private in.devmedi.kiosk.module.auth.entity.User user;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CompletedCaseEntity() {
    }

    public CompletedCaseEntity(String caseId) {
        this.caseId = caseId;
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    public String getCaseId() {
        return caseId;
    }

    public in.devmedi.kiosk.module.auth.entity.User getUser() {
        return user;
    }

    public void setUser(in.devmedi.kiosk.module.auth.entity.User user) {
        this.user = user;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}