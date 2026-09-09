package in.devmedi.kiosk.module.clinical.summary;

/**
 * One summarized entry of a clinical intake conversation.
 *
 * <p>Preserves the original question context and the patient's unchanged
 * free-text answer. No diagnosis or clinical fact is inferred here.</p>
 *
 * @param questionId   stable question id (e.g. {@code hpi_onset} or {@code dashavidha_sara})
 * @param questionType SOCRATES facet or AYUSH parameter name
 * @param questionText the question the patient was asked
 * @param answer       the patient's original answer, unchanged
 */
public record ClinicalSummaryEntry(String questionId,
                                   String questionType,
                                   String questionText,
                                   String answer) {
}