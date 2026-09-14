package in.devmedi.kiosk.module.physician.clinicalrecord;

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
 * The physician's final consultation record for a case.
 *
 * <p>Every field here is physician-authored: assessment, plan, advice, and
 * follow-up. It is the "FINAL CLINICAL RECORD" after the review loop. AI
 * summaries live separately (clinical_summaries) and are never copied into
 * this record without an explicit physician action.</p>
 */
@Entity
@Table(name = "consultations",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"case_id"},
                name = "uq_consultation_case"))
public class Consultation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false)
    private CompletedCaseEntity completedCase;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "physician_user_id", nullable = false)
    private User physician;

    @Column(columnDefinition = "TEXT")
    private String assessment;

    @Column(columnDefinition = "TEXT")
    private String plan;

    @Column(columnDefinition = "TEXT")
    private String advice;

    @Column(name = "follow_up", length = 1000)
    private String followUp;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ClinicalRecordStatus status = ClinicalRecordStatus.DRAFT;

    @Column(name = "finalized_at")
    private Instant finalizedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Consultation() {
    }

    public Consultation(CompletedCaseEntity completedCase, User physician) {
        this.completedCase = completedCase;
        this.physician = physician;
        this.status = ClinicalRecordStatus.DRAFT;
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

    /** Saves / updates the physician-authored record. */
    public void update(String assessment, String plan, String advice, String followUp) {
        this.assessment = assessment;
        this.plan = plan;
        this.advice = advice;
        this.followUp = followUp;
        this.status = ClinicalRecordStatus.DRAFT;
    }

    /** Finalizes the clinical record. */
    public void finalizeRecord() {
        this.status = ClinicalRecordStatus.FINALIZED;
        this.finalizedAt = Instant.now();
    }

    public Long getId() { return id; }
    public CompletedCaseEntity getCompletedCase() { return completedCase; }
    public User getPhysician() { return physician; }
    public String getAssessment() { return assessment; }
    public String getPlan() { return plan; }
    public String getAdvice() { return advice; }
    public String getFollowUp() { return followUp; }
    public ClinicalRecordStatus getStatus() { return status; }
    public Instant getFinalizedAt() { return finalizedAt; }
}