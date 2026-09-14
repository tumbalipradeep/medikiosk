package in.devmedi.kiosk.module.physician.assignment;

import in.devmedi.kiosk.module.auth.entity.User;
import in.devmedi.kiosk.module.physician.entity.CompletedCaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * Assignment of a physician to a completed clinical case.
 *
 * <p>A case with at least one ACTIVE assignment is private to the assigned
 * physician(s). A case with no assignment is in the shared pool, visible to
 * every physician. This is enforced by the access-control service, so a
 * physician can never silently open every patient in the system.</p>
 */
@Entity
@Table(name = "case_assignments",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"case_id", "physician_user_id"},
                name = "uq_case_assignment"))
public class CaseAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false)
    private CompletedCaseEntity completedCase;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "physician_user_id", nullable = false)
    private User physician;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by")
    private User assignedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssignmentStatus status = AssignmentStatus.ACTIVE;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CaseAssignment() {
    }

    public CaseAssignment(CompletedCaseEntity completedCase, User physician, User assignedBy) {
        this.completedCase = completedCase;
        this.physician = physician;
        this.assignedBy = assignedBy;
        this.status = AssignmentStatus.ACTIVE;
        this.assignedAt = Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public void complete() {
        this.status = AssignmentStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    public void unassign() {
        this.status = AssignmentStatus.UNASSIGNED;
        this.completedAt = Instant.now();
    }

    public Long getId() { return id; }
    public CompletedCaseEntity getCompletedCase() { return completedCase; }
    public User getPhysician() { return physician; }
    public User getAssignedBy() { return assignedBy; }
    public AssignmentStatus getStatus() { return status; }
    public Instant getAssignedAt() { return assignedAt; }
    public Instant getCompletedAt() { return completedAt; }
}