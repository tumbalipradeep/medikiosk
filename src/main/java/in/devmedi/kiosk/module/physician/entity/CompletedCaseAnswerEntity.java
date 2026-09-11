package in.devmedi.kiosk.module.physician.entity;

import in.devmedi.kiosk.module.clinical.dialogue.AnswerSource;
import in.devmedi.kiosk.module.clinical.dialogue.ClinicalAnswer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * One captured clinical answer of a persisted completed case.
 *
 * <p>Stores every field of the original {@link ClinicalAnswer} verbatim —
 * question id, clinical section, question type, canonical question text, the
 * displayed question text, the question source (AI or deterministic), and the
 * raw patient answer — along with {@code answerOrder} so the original
 * answering order is preserved exactly on retrieval. Nothing is summarized or
 * modified before persistence.</p>
 */
@Entity
@Table(name = "completed_case_answers", uniqueConstraints = {
        @UniqueConstraint(name = "uq_completed_case_answers_order",
                columnNames = {"case_id", "answer_order"})
})
public class CompletedCaseAnswerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false)
    private CompletedCaseEntity completedCase;

    @Column(name = "answer_order", nullable = false)
    private int answerOrder;

    @Column(name = "question_id", nullable = false, length = 64)
    private String questionId;

    @Column(nullable = false, length = 32)
    private String section;

    @Column(name = "question_type", nullable = false, length = 64)
    private String questionType;

    @Column(name = "question_text", nullable = false, length = 1000)
    private String questionText;

    @Column(name = "displayed_question_text", nullable = false, length = 1000)
    private String displayedQuestionText;

    @Column(name = "question_source", nullable = false, length = 16)
    private String questionSource;

    @Column(name = "answer_source", nullable = false, length = 8)
    private String answerSource;

    @Column(name = "answer_language", nullable = false, length = 16)
    private String answerLanguage;

    @Column(nullable = false, length = 4000)
    private String answer;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CompletedCaseAnswerEntity() {
    }

    public static CompletedCaseAnswerEntity from(CompletedCaseEntity completedCase,
                                                 int answerOrder,
                                                 ClinicalAnswer clinicalAnswer) {
        CompletedCaseAnswerEntity entity = new CompletedCaseAnswerEntity();
        entity.completedCase = completedCase;
        entity.answerOrder = answerOrder;
        entity.questionId = clinicalAnswer.questionId();
        entity.section = clinicalAnswer.section();
        entity.questionType = clinicalAnswer.questionType();
        entity.questionText = clinicalAnswer.questionText();
        entity.displayedQuestionText = clinicalAnswer.displayedQuestionText() == null
                ? clinicalAnswer.questionText() : clinicalAnswer.displayedQuestionText();
        entity.questionSource = clinicalAnswer.questionSource() == null
                ? in.devmedi.kiosk.module.clinical.dialogue.QuestionSource.DETERMINISTIC.name()
                : clinicalAnswer.questionSource().name();
        entity.answerSource = clinicalAnswer.answerSource() == null
                ? AnswerSource.TEXT.name()
                : clinicalAnswer.answerSource().name();
        entity.answerLanguage = clinicalAnswer.language() == null
                ? ClinicalAnswer.DEFAULT_LANGUAGE
                : clinicalAnswer.language();
        entity.answer = clinicalAnswer.answer();
        return entity;
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    public ClinicalAnswer toClinicalAnswer() {
        return new ClinicalAnswer(questionId, section, questionType, questionText,
                displayedQuestionText == null ? questionText : displayedQuestionText, answer,
                in.devmedi.kiosk.module.clinical.dialogue.QuestionSource
                        .valueOf(questionSource == null
                                ? in.devmedi.kiosk.module.clinical.dialogue.QuestionSource.DETERMINISTIC.name()
                                : questionSource),
                AnswerSource.valueOf(answerSource == null ? AnswerSource.TEXT.name() : answerSource),
                answerLanguage == null ? ClinicalAnswer.DEFAULT_LANGUAGE : answerLanguage);
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

    public String getQuestionId() {
        return questionId;
    }

    public String getSection() {
        return section;
    }

    public String getQuestionType() {
        return questionType;
    }

    public String getQuestionText() {
        return questionText;
    }

    public String getDisplayedQuestionText() {
        return displayedQuestionText;
    }

    public String getQuestionSource() {
        return questionSource;
    }

    public String getAnswerSource() {
        return answerSource;
    }

    public String getAnswerLanguage() {
        return answerLanguage;
    }

    public String getAnswer() {
        return answer;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}