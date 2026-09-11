package in.devmedi.kiosk.module.clinical.dialogue;

/**
 * A single structured clinical intake answer.
 *
 * <p>Captures what the patient was asked, what wording was displayed, and what
 * they answered. The canonical question text and objective are always preserved
 * verbatim; the displayed wording may differ when a validated AI provider
 * phrased it, and the source records which one was actually shown. A
 * convenience constructor and {@link #from(IntakeQuestion, String)} keep the
 * plain deterministic path compact.</p>
 *
 * @param questionId             stable question id (e.g. {@code hpi_onset} or {@code dashavidha_sara})
 * @param section                section the question belongs to (HPI section, DASHAVIDHA, or AHARA_VIHARA)
 * @param questionType           question type: SOCRATES facet or AYUSH parameter name
 * @param questionText           canonical deterministic question text
 * @param displayedQuestionText  question text actually shown to the patient (equals {@code questionText} when deterministic)
 * @param answer                 the patient's free-text answer (verbatim, source of truth)
 * @param questionSource         whether the displayed wording was AI-generated or deterministic
 */
public record ClinicalAnswer(String questionId,
                             String section,
                             String questionType,
                             String questionText,
                             String displayedQuestionText,
                             String answer,
                             QuestionSource questionSource) {

    /** Compact constructor for the plain deterministic path (displayed = canonical). */
    public ClinicalAnswer(String questionId,
                          String section,
                          String questionType,
                          String questionText,
                          String answer) {
        this(questionId, section, questionType, questionText, questionText, answer, QuestionSource.DETERMINISTIC);
    }

    public static ClinicalAnswer from(IntakeQuestion question, String answer) {
        return new ClinicalAnswer(question.id(),
                question.section(),
                question.type(),
                question.text(),
                answer);
    }

    /**
     * Records an answer where the displayed wording and its source are known.
     *
     * @param canonical      the deterministic question that was asked
     * @param displayedText  the wording actually shown to the patient
     * @param answer         the patient's verbatim answer
     * @param source         whether the displayed wording was AI-generated or deterministic
     */
    public static ClinicalAnswer from(IntakeQuestion canonical, String displayedText, String answer, QuestionSource source) {
        return new ClinicalAnswer(canonical.id(),
                canonical.section(),
                canonical.type(),
                canonical.text(),
                displayedText == null ? canonical.text() : displayedText,
                answer == null ? "" : answer,
                source == null ? QuestionSource.DETERMINISTIC : source);
    }
}