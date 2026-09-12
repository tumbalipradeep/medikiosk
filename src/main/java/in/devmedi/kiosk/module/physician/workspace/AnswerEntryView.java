package in.devmedi.kiosk.module.physician.workspace;

import java.util.List;

/**
 * One captured intake answer with its source evidence, derived flags, and the
 * physician's review decision. Original evidence is preserved verbatim; review
 * decisions and amendments are carried alongside it, never replacing it.
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
                              ReviewView review) {

    public boolean reworded() {
        return displayedQuestionText != null && !displayedQuestionText.equals(questionText);
    }
}