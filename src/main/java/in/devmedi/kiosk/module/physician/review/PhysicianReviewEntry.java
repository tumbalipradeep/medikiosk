package in.devmedi.kiosk.module.physician.review;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * A physician's review decision against one captured answer of a completed case.
 *
 * <p>The row is keyed uniquely on the stable pair {@code (case_id, answer_order)},
 * which mirrors the stable identity pair in {@code completed_case_answers}.
 * Review decisions are written alongside the original patient evidence; the
 * original answer text is never overwritten.</p>
 *
 * <p>Deleting a completed case cascades to its review entries via the
 * {@code ON DELETE CASCADE} FK, so callers never have to delete entries
 * independently when a case is removed.</p>
 */
@Entity
@Table(name = "physician_review_entries", uniqueConstraints = {
        @UniqueConstraint(name = "uq_physician_review_entry_order",
                columnNames = {"case_id", "answer_order"})
}, indexes = {
        @Index(name = "idx_physician_review_entries_case",
                columnList = "case_id")
})
public class PhysicianReviewEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false)
    private CompletedCaseEntity completedCase;

    @Column(name = "answer_order", nullable = false)
    private int answerOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReviewDecision decision;

    @Column(name = "amended_text", length = 4000)
    private String amendedText;

    @Column(length = 1000)
    private String rationale;

    @Column(name = "reviewer_username", nullable = false, length = 100)
    private String reviewerUsername;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PhysicianReviewEntry() {
    }

    public static PhysicianReviewEntry of(CompletedCaseEntity completedCase,
                                           int answerOrder,
                                           ReviewDecision decision,
                                           String amendedText,
                                           String rationale,
                                           String reviewerUsername) {
        PhysicianReviewEntry entry = new PhysicianReviewEntry();
        entry.completedCase = completedCase;
        entry.answerOrder = answerOrder;
        entry.decision = decision;
        entry.amendedText = amendedText;
        entry.rationale = rationale;
        entry.reviewerUsername = reviewerUsername;
        entry.decidedAt = Instant.now();
        entry.updatedAt = entry.decidedAt;
        return entry;
    }

    /**
     * Re-decides an existing review entry in place (same stable
     * {@code (case_id, answer_order)} key, reviewed representation replaced).
     */
    public void apply(ReviewDecision decision, String amendedText, String rationale,
                      String reviewerUsername) {
        this.decision = decision;
        this.amendedText = amendedText;
        this.rationale = rationale;
        this.reviewerUsername = reviewerUsername;
        this.decidedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        if (this.decidedAt == null) {
            this.decidedAt = Instant.now();
        }
        if (this.updatedAt == null) {
            this.updatedAt = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
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

    public ReviewDecision getDecision() {
        return decision;
    }

    public String getAmendedText() {
        return amendedText;
    }

    public String getRationale() {
        return rationale;
    }

    public String getReviewerUsername() {
        return reviewerUsername;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}