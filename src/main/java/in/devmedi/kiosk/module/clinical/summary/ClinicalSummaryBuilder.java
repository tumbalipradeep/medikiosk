package in.devmedi.kiosk.module.clinical.summary;

import in.devmedi.kiosk.module.clinical.dialogue.ClinicalAnswer;
import in.devmedi.kiosk.module.clinical.dialogue.ClinicalConversationResult;
import in.devmedi.kiosk.module.clinical.dialogue.ClinicalSection;
import in.devmedi.kiosk.module.clinical.dialogue.IntakeQuestion;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic builder that converts the in-memory
 * {@link ClinicalConversationResult} of a patient intake conversation into a
 * structured physician-readable {@link ClinicalSummary}.
 *
 * <p>Answers are organized into the existing clinical sections in a fixed
 * canonical order — History of Present Illness / SOCRATES, Dashavidha
 * Pariksha, Ahara-Vihara — while the original answering order and the original
 * answer text are preserved exactly. The layer never infers diagnoses, never
 * scores or interprets Dashavidha answers, never calls an AI provider, and
 * never persists anything. Empty, partial, and even {@code null} results are
 * handled safely and still yield the canonical (possibly empty) sections.</p>
 */
@Service
public class ClinicalSummaryBuilder {

    public static final String HPI_SECTION_NAME = "History of Present Illness / SOCRATES";
    public static final String DASHAVIDHA_SECTION_NAME = "Dashavidha Pariksha";
    public static final String AHARA_VIHARA_SECTION_NAME = "Ahara-Vihara";

    /** Default {@code OTHER} bucket for sections the builder does not recognize. */
    private static final String OTHER_PLACEHOLDER = "Other";

    /**
     * Summarizes a conversation result, deterministically grouping every
     * captured answer into its clinical section.
     *
     * @param result the in-memory conversation result (may be {@code null})
     * @return a summary whose sections appear in canonical order; no captured answer is dropped
     */
    public ClinicalSummary summarize(ClinicalConversationResult result) {
        Map<String, List<ClinicalSummaryEntry>> buckets = new LinkedHashMap<>();
        buckets.put(HPI_SECTION_NAME, new ArrayList<>());
        buckets.put(DASHAVIDHA_SECTION_NAME, new ArrayList<>());
        buckets.put(AHARA_VIHARA_SECTION_NAME, new ArrayList<>());

        int answeredCount = 0;
        if (result != null) {
            answeredCount = result.size();
            for (ClinicalAnswer answer : result.all()) {
                ClinicalSummaryEntry entry = new ClinicalSummaryEntry(
                        answer.questionId(),
                        answer.questionType(),
                        displayedQuestionText(answer),
                        answer.answer());
                buckets.computeIfAbsent(sectionName(answer.section()), ignored -> new ArrayList<>())
                        .add(entry);
            }
        }

        List<ClinicalSummarySection> sections = buckets.entrySet().stream()
                .map(entry -> new ClinicalSummarySection(entry.getKey(), List.copyOf(entry.getValue())))
                .toList();
        return new ClinicalSummary(sections, answeredCount);
    }

    /**
     * The wording actually shown to the patient (may be AI-rephrased), falling
     * back to the canonical deterministic text when no displayed wording exists.
     */
    private static String displayedQuestionText(ClinicalAnswer answer) {
        String displayed = answer.displayedQuestionText();
        return displayed == null || displayed.isBlank() ? answer.questionText() : displayed;
    }

    /**
     * Maps a captured answer's section to its canonical summary section. The
     * two HPI/SOCRATES clinical sections share one summary section. Any
     * unrecognized section is preserved under its own name so that no answer
     * is ever lost or relabeled.
     */
    private static String sectionName(String section) {
        if (section == null) {
            return OTHER_PLACEHOLDER;
        }
        if (ClinicalSection.CHIEF_COMPLAINT.name().equals(section)
                || ClinicalSection.HISTORY_OF_PRESENT_ILLNESS.name().equals(section)) {
            return HPI_SECTION_NAME;
        }
        if (IntakeQuestion.DASHAVIDHA_SECTION.equals(section)) {
            return DASHAVIDHA_SECTION_NAME;
        }
        if (IntakeQuestion.AHARA_VIHARA_SECTION.equals(section)) {
            return AHARA_VIHARA_SECTION_NAME;
        }
        return section;
    }
}