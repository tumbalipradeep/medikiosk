package in.devmedi.kiosk.module.clinical.dialogue;

/**
 * A single structured clinical intake answer.
 *
 * <p>Captures what the patient was asked, what wording was displayed, how the
 * answer entered the pipeline, in which patient language, and what they
 * answered. The canonical question text and objective are always preserved
 * verbatim; the displayed wording may differ when a validated AI provider
 * phrased it, and the source records which one was actually shown. The answer
 * itself is always the patient's verbatim text regardless of whether it was
 * typed or voice-transcribed. The {@code language} is the BCP-47 tag of the
 * patient-facing language at the time the answer was captured (English when no
 * language was selected). Convenience constructors and {@link #from(IntakeQuestion, String)}
 * keep the plain deterministic path compact.</p>
 *
 * @param questionId             stable question id (e.g. {@code hpi_onset} or {@code dashavidha_sara})
 * @param section                section the question belongs to (HPI section, DASHAVIDHA, or AHARA_VIHARA)
 * @param questionType           question type: SOCRATES facet or AYUSH parameter name
 * @param questionText           canonical deterministic question text
 * @param displayedQuestionText  question text actually shown to the patient (equals {@code questionText} when deterministic)
 * @param answer                 the patient's free-text answer (verbatim, source of truth)
 * @param questionSource         whether the displayed wording was AI-generated or deterministic
 * @param answerSource           whether the answer was typed or voice-transcribed
 * @param language               BCP-47 tag of the patient-facing language used for this step
 */
public record ClinicalAnswer(String questionId,
                             String section,
                             String questionType,
                             String questionText,
                             String displayedQuestionText,
                             String answer,
                             QuestionSource questionSource,
                             AnswerSource answerSource,
                             String language) {

    /** Deterministic default patient-facing language (English). */
    public static final String DEFAULT_LANGUAGE = "en-IN";

    public ClinicalAnswer {
        if (displayedQuestionText == null || displayedQuestionText.isBlank()) {
            displayedQuestionText = questionText;
        }
        if (answer == null) {
            answer = "";
        }
        if (questionSource == null) {
            questionSource = QuestionSource.DETERMINISTIC;
        }
        if (answerSource == null) {
            answerSource = AnswerSource.TEXT;
        }
        if (language == null || language.isBlank()) {
            language = DEFAULT_LANGUAGE;
        }
    }

    /** Convenience constructor for the full clinical record with no language (defaults to English). */
    public ClinicalAnswer(String questionId,
                          String section,
                          String questionType,
                          String questionText,
                          String displayedQuestionText,
                          String answer,
                          QuestionSource questionSource,
                          AnswerSource answerSource) {
        this(questionId, section, questionType, questionText, displayedQuestionText,
                answer, questionSource, answerSource, DEFAULT_LANGUAGE);
    }

    /** Convenience constructor for the deterministic path with a known displayed wording. */
    public ClinicalAnswer(String questionId,
                          String section,
                          String questionType,
                          String questionText,
                          String displayedQuestionText,
                          String answer,
                          QuestionSource questionSource) {
        this(questionId, section, questionType, questionText, displayedQuestionText,
                answer, questionSource, AnswerSource.TEXT);
    }

    /** Compact constructor for the plain deterministic path (displayed = canonical). */
    public ClinicalAnswer(String questionId,
                          String section,
                          String questionType,
                          String questionText,
                          String answer) {
        this(questionId, section, questionType, questionText, questionText,
                answer, QuestionSource.DETERMINISTIC, AnswerSource.TEXT);
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
        return from(canonical, displayedText, answer, source, AnswerSource.TEXT);
    }

    /**
     * Records an answer where the displayed wording, its source, and the
     * patient's entry method are all known.
     *
     * @param canonical      the deterministic question that was asked
     * @param displayedText  the wording actually shown to the patient
     * @param answer         the patient's verbatim answer
     * @param source         whether the displayed wording was AI-generated or deterministic
     * @param answerSource   whether the answer was typed or voice-transcribed
     */
    public static ClinicalAnswer from(IntakeQuestion canonical,
                                      String displayedText,
                                      String answer,
                                      QuestionSource source,
                                      AnswerSource answerSource) {
        return from(canonical, displayedText, answer, source, answerSource, DEFAULT_LANGUAGE);
    }

    /**
     * Records an answer with every detail known, including the patient-facing
     * language used for this intake step. The language is presentation-layer
     * state captured alongside the clinical truth so the record stays
     * complete; it never influences clinical content.
     *
     * @param canonical      the deterministic question that was asked
     * @param displayedText  the wording actually shown to the patient
     * @param answer         the patient's verbatim answer
     * @param source         whether the displayed wording was AI-generated or deterministic
     * @param answerSource   whether the answer was typed or voice-transcribed
     * @param language       the patient-facing language (BCP-47) used for this step
     */
    public static ClinicalAnswer from(IntakeQuestion canonical,
                                      String displayedText,
                                      String answer,
                                      QuestionSource source,
                                      AnswerSource answerSource,
                                      String language) {
        return new ClinicalAnswer(canonical.id(),
                canonical.section(),
                canonical.type(),
                canonical.text(),
                displayedText == null ? canonical.text() : displayedText,
                answer == null ? "" : answer,
                source == null ? QuestionSource.DETERMINISTIC : source,
                answerSource,
                language);
    }
}