package in.devmedi.kiosk.module.patient.correction;

import in.devmedi.kiosk.module.physician.entity.CompletedCaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * A patient's correction of one captured intake answer.
 *
 * <p>Corrections are additive evidence: the original captured answer in
 * {@code completed_case_answers} is never modified. Each correction carries its
 * own immutable snapshot of the original answer as it was when the correction
 * was submitted, so the original evidence always remains reconstructible and
 * auditable even if the case's answers are later amended through physician
 * review.</p>
 *
 * <p>One current correction exists per {@code (case_id, answer_order)}; a new
 * submission by the owning patient replaces their pending correction. A
 * physician's clinical decision about which value to use remains a separate
 * {@code PHYSICIAN_REVIEW} state and is never silently overwritten here.</p>
 *
 * <p>Deleting a completed case cascades to its corrections via the FK.</p>
 */
@Entity
@Table(name = "patient_answer_corrections", uniqueConstraints = {
        @UniqueConstraint(name = "uq_patient_answer_corrections_order",
                columnNames = {"case_id", "answer_order"})
}, indexes = {
        @Index(name = "idx_patient_answer_corrections_case",
                columnList = "case_id")
})
public class PatientAnswerCorrection {

    /**
     * Lifecycle of a correction. The application writes exactly one state:
     * {@code SUBMITTED} (awaiting physician attention). A physician's clinical
     * decision about which value to use is deliberately NOT recorded here —
     * it lives in {@code physician_review_entries} and is audited as a
     * {@code PHYSICIAN_REVIEW} event, keeping the original evidence, the
     * patient's correction, and the physician's decision as three distinct,
     * independently auditable artifacts.
     */
    public enum CorrectionStatus {
        SUBMITTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false)
    private CompletedCaseEntity completedCase;

    @Column(name = "answer_order", nullable = false)
    private int answerOrder;

    /** Immutable snapshot of the original answer at correction time. */
    @Column(name = "original_answer", nullable = false, length = 4000)
    private String originalAnswer;

    @Column(name = "corrected_answer", nullable = false, length = 4000)
    private String correctedAnswer;

    @Column(length = 1000)
    private String reason;

    @Column(name = "corrected_by", nullable = false, length = 100)
    private String correctedBy;

    @Column(name = "corrected_at", nullable = false)
    private Instant correctedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CorrectionStatus status;

    protected PatientAnswerCorrection() {
    }

    private PatientAnswerCorrection(CompletedCaseEntity completedCase,
                                    int answerOrder,
                                    String originalAnswer,
                                    String correctedAnswer,
                                    String reason,
                                    String correctedBy,
                                    Instant correctedAt) {
        this.completedCase = completedCase;
        this.answerOrder = answerOrder;
        this.originalAnswer = originalAnswer;
        this.correctedAnswer = correctedAnswer;
        this.reason = reason;
        this.correctedBy = correctedBy;
        this.correctedAt = correctedAt;
        this.status = CorrectionStatus.SUBMITTED;
    }

    /**
     * Creates a new pending correction. The server supplies the original-answer
     * snapshot from the persisted answer row itself; the client never supplies it.
     */
    public static PatientAnswerCorrection submit(CompletedCaseEntity completedCase,
                                                 int answerOrder,
                                                 String originalAnswer,
                                                 String correctedAnswer,
                                                 String reason,
                                                 String correctedBy,
                                                 Instant correctedAt) {
        return new PatientAnswerCorrection(completedCase, answerOrder, originalAnswer,
                correctedAnswer, reason, correctedBy, correctedAt);
    }

    @PreUpdate
    void onUpdate() {
        // correctedAt and the original snapshot stay immutable; nothing to do.
    }

    public Long getId() {
        return id;
    }

    public CompletedCaseEntity getCompletedCase() {
        return completedCase;
    }

    public int getAnswerOrder() {
        return answerOrder;
    }

    public String getOriginalAnswer() {
        return originalAnswer;
    }

    public String getCorrectedAnswer() {
        return correctedAnswer;
    }

    public String getReason() {
        return reason;
    }

    public String getCorrectedBy() {
        return correctedBy;
    }

    public Instant getCorrectedAt() {
        return correctedAt;
    }

    public CorrectionStatus getStatus() {
        return status;
    }
}
