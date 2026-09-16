package in.devmedi.kiosk.module.physician.workspace;

import java.util.List;

/**
 * One captured intake answer with its source evidence, derived flags, the
 * physician's review decision, and any patient correction provenance.
 * Original evidence is preserved verbatim; review decisions, amendments, and
 * patient corrections are carried alongside it, never replacing it.
 */
public record AnswerEntryView(int answerOrder,
                              String questionId,
                              String section,
                              String questionType,
                              String questionText,
                              String displayedQuestionText,
                              String questionSource,
                              String questionSourceLabel,
                              String answerSource,
                              String answerSourceLabel,
                              String languageCode,
                              String languageLabel,
                              String answer,
                              List<DerivedFlagView> flags,
                              ReviewView review,
                              CorrectionView correction) {

    public boolean reworded() {
        return displayedQuestionText != null && !displayedQuestionText.equals(questionText);
    }

    /**
     * Patient-correction provenance beside an answer. The original evidence
     * ({@code answer} above) remains authoritative; this carries what the
     * patient corrected it to, when, and by whom — never a silent rewrite.
     */
    public record CorrectionView(boolean present,
                                 String originalAnswer,
                                 String correctedAnswer,
                                 String reason,
                                 String correctedBy,
                                 java.time.Instant correctedAt,
                                 String status) {
    }
}
