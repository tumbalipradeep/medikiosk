package in.devmedi.kiosk.module.encounter;

import in.devmedi.kiosk.module.auth.entity.User;
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

import java.time.Instant;

/**
 * A single patient encounter (one visit / consultation) and its lifecycle.
 *
 * <p>The encounter is the workflow container: start → intake → submitted →
 * under review → completed. Once intake is submitted the {@code caseId} points
 * at the immutable clinical record (completed_case row) that physicians
 * review.</p>
 */
@Entity
@Table(name = "encounters")
public class Encounter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_user_id", nullable = false)
    private User patient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private EncounterStatus status;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "case_id", length = 64)
    private String caseId;

    @Column(length = 2000)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Encounter() {
    }

    public Encounter(User patient) {
        this.patient = patient;
        this.status = EncounterStatus.IN_PROGRESS;
        this.startedAt = Instant.now();
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

    /** Transitions IN_PROGRESS → SUBMITTED with the referenced case id. */
    public void submit(String caseId) {
        if (this.status != EncounterStatus.IN_PROGRESS) {
            throw new IllegalStateException("Cannot submit an encounter in state " + this.status);
        }
        this.status = EncounterStatus.SUBMITTED;
        this.caseId = caseId;
        this.submittedAt = Instant.now();
    }

    /** Transitions SUBMITTED → UNDER_REVIEW (a physician took the case up). */
    public void markUnderReview() {
        if (this.status != EncounterStatus.SUBMITTED && this.status != EncounterStatus.UNDER_REVIEW) {
            throw new IllegalStateException("Cannot move an encounter in state " + this.status + " to UNDER_REVIEW");
        }
        this.status = EncounterStatus.UNDER_REVIEW;
    }

    /** Transitions SUBSMITTED/UNDER_REVIEW → COMPLETED (clinical record finalized). */
    public void complete() {
        if (this.status != EncounterStatus.SUBMITTED && this.status != EncounterStatus.UNDER_REVIEW) {
            throw new IllegalStateException("Cannot complete an encounter in state " + this.status);
        }
        this.status = EncounterStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    public void cancel() {
        if (this.status != EncounterStatus.IN_PROGRESS) {
            throw new IllegalStateException("Only an in-progress encounter can be cancelled");
        }
        this.status = EncounterStatus.CANCELLED;
    }

    public Long getId() {
        return id;
    }

    public User getPatient() {
        return patient;
    }

    public EncounterStatus getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public String getCaseId() {
        return caseId;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}