package in.devmedi.kiosk.module.clinical.triage;

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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * A physician's documented assessment of one triage flag on a case.
 *
 * <p>The flag itself is always re-derived deterministically from the patient's
 * own words; this row captures only the human, clinical overlay about it. A
 * cleared or seen flag is never a diagnosis — it is an accountable clinical
 * review decision.</p>
 */
@Entity
@Table(name = "red_flag_assessments",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"case_id", "flag_id"},
                name = "uq_flag_case"))
public class RedFlagAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false)
    private CompletedCaseEntity completedCase;

    @Column(name = "flag_id", nullable = false, length = 64)
    private String flagId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TriageSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TriageSource source;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assessed_by")
    private User assessedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FlagAssessmentAction action;

    @Column(length = 1000)
    private String note;

    @Column(name = "assessed_at", nullable = false, updatable = false)
    private Instant assessedAt;

    protected RedFlagAssessment() {
    }

    public RedFlagAssessment(CompletedCaseEntity completedCase,
                             String flagId,
                             TriageSeverity severity,
                             TriageSource source,
                             User assessedBy,
                             FlagAssessmentAction action,
                             String note) {
        this.completedCase = completedCase;
        this.flagId = flagId;
        this.severity = severity == null ? TriageSeverity.NONE : severity;
        this.source = source == null ? TriageSource.SYSTEM_DETECTED : source;
        this.assessedBy = assessedBy;
        this.action = action == null ? FlagAssessmentAction.SEEN : action;
        this.note = note;
    }

    @PrePersist
    protected void onCreate() {
        this.assessedAt = Instant.now();
    }

    public Long getId() { return id; }
    public CompletedCaseEntity getCompletedCase() { return completedCase; }
    public String getFlagId() { return flagId; }
    public TriageSeverity getSeverity() { return severity; }
    public TriageSource getSource() { return source; }
    public User getAssessedBy() { return assessedBy; }
    public FlagAssessmentAction getAction() { return action; }
    public String getNote() { return note; }
    public Instant getAssessedAt() { return assessedAt; }

    /** Physician documents a clinical action on this flag. */
    public void assess(User physician, FlagAssessmentAction action, String note) {
        this.assessedBy = physician;
        this.action = action;
        this.note = note;
        this.source = TriageSource.PHYSICIAN_ASSESSMENT;
    }
}