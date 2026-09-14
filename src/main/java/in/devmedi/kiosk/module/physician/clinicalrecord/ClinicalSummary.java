package in.devmedi.kiosk.module.physician.clinicalrecord;

import in.devmedi.kiosk.module.auth.entity.User;
import in.devmedi.kiosk.module.clinical.provenance.ClinicalProvenance;
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
 * One editable, physician-facing clinical summary section for a case.
 *
 * <p>The summary may be seeded from AI_ASSISTED or SYSTEM_GENERATED content
 * (e.g. a deterministic summary of the intake) but its final wording is
 * physician-authored: the physician reviews, amends, then explicitly accepts
 * it. Provenance records who produced the current content, and the status is
 * only ACCEPTED after an explicit physician action — so AI output can never
 * silently become a physician-authored summary.</p>
 */
@Entity
@Table(name = "clinical_summaries",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"case_id", "section"},
                name = "uq_clinical_summary_case_section"))
public class ClinicalSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false)
    private CompletedCaseEntity completedCase;

    @Column(nullable = false, length = 24)
    private String section;

    @Column(nullable = false, length = 8000)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ClinicalProvenance provenance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ClinicalRecordStatus status = ClinicalRecordStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ClinicalSummary() {
    }

    public ClinicalSummary(CompletedCaseEntity completedCase,
                           String section,
                           String content,
                           ClinicalProvenance provenance,
                           User createdBy) {
        this.completedCase = completedCase;
        this.section = section;
        this.content = content;
        this.provenance = provenance == null ? ClinicalProvenance.PHYSICIAN_ENTERED : provenance;
        this.createdBy = createdBy;
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

    /** Physician edits: provenance becomes PHYSICIAN_ENTERED. */
    public void amend(String newContent) {
        this.content = newContent;
        this.provenance = ClinicalProvenance.PHYSICIAN_ENTERED;
        this.status = ClinicalRecordStatus.DRAFT;
    }

    public void accept(String reviewedContent) {
        this.content = reviewedContent == null ? this.content : reviewedContent;
        this.provenance = ClinicalProvenance.PHYSICIAN_ENTERED;
        this.status = ClinicalRecordStatus.ACCEPTED;
    }

    public void reject() {
        this.status = ClinicalRecordStatus.REJECTED;
    }

    public Long getId() { return id; }
    public CompletedCaseEntity getCompletedCase() { return completedCase; }
    public String getSection() { return section; }
    public String getContent() { return content; }
    public ClinicalProvenance getProvenance() { return provenance; }
    public ClinicalRecordStatus getStatus() { return status; }
    public User getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}