package in.devmedi.kiosk.module.clinical.history;

import in.devmedi.kiosk.module.clinical.dialogue.ClinicalAnswer;
import in.devmedi.kiosk.module.clinical.dialogue.IntakeQuestion;
import in.devmedi.kiosk.module.clinical.provenance.ClinicalProvenance;
import in.devmedi.kiosk.module.physician.service.CompletedCase;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Maps a completed intake conversation into the structured clinical history.
 *
 * <p>Every answer becomes one history item in the category implied by its
 * section (chief complaint, HPI, Dashavidha, Ahara-Vihara), keyed by its stable
 * question id/parameter. Answers are always {@link
 * ClinicalProvenance#PATIENT_REPORTED}; provenance is never invented here. The
 * mapping is idempotent in the store, so re-running an intake or re-seeding
 * cannot duplicate rows.</p>
 */
@Service
public class ClinicalHistorySeedingService {

    private final ClinicalHistoryService historyService;

    public ClinicalHistorySeedingService(ClinicalHistoryService historyService) {
        this.historyService = historyService;
    }

    /** Persists the structured history for a completed intake. */
    public void seedFromCompletedCase(CompletedCase completedCase) {
        if (completedCase == null || completedCase.userId() == null) {
            return;
        }
        List<HistoryDatum> items = toHistoryData(completedCase);
        if (!items.isEmpty()) {
            historyService.recordAll(completedCase.userId(), items);
        }
    }

    /** Builds the history data without persisting (unit-testable). */
    public List<HistoryDatum> toHistoryData(CompletedCase completedCase) {
        List<HistoryDatum> items = new ArrayList<>();
        if (completedCase == null || completedCase.result() == null) {
            return items;
        }
        for (ClinicalAnswer answer : completedCase.result().all()) {
            if (answer.answer() == null || answer.answer().isBlank()) {
                continue;
            }
            ClinicalHistoryCategory category = categoryFor(answer);
            if (category == null) {
                continue;
            }
            String conceptKey = conceptKeyFor(answer);
            String label = readableLabel(answer);
            items.add(new HistoryDatum(
                    category,
                    conceptKey,
                    label,
                    answer.answer(),
                    answer.questionText(),
                    ClinicalProvenance.PATIENT_REPORTED,
                    sourceLabelFor(answer)));
        }
        return items;
    }

    private static ClinicalHistoryCategory categoryFor(ClinicalAnswer answer) {
        return switch (answer.section()) {
            case IntakeQuestion.DASHAVIDHA_SECTION -> ClinicalHistoryCategory.DASHAVIDHA;
            case IntakeQuestion.AHARA_VIHARA_SECTION -> ClinicalHistoryCategory.AHARA_VIHARA;
            case "CHIEF_COMPLAINT" -> ClinicalHistoryCategory.CHIEF_COMPLAINT;
            case "HISTORY_OF_PRESENT_ILLNESS" -> ClinicalHistoryCategory.HPI;
            default -> null;
        };
    }

    private static String conceptKeyFor(ClinicalAnswer answer) {
        if (answer.questionId() != null && !answer.questionId().isBlank()) {
            return answer.questionId();
        }
        return slug(answer.section() + "-" + answer.questionType());
    }

    private static String readableLabel(ClinicalAnswer answer) {
        String type = answer.questionType();
        if (type == null || type.isBlank()) {
            return answer.questionId();
        }
        return type.replace('_', ' ').toLowerCase(Locale.ROOT);
    }

    private static String sourceLabelFor(ClinicalAnswer answer) {
        boolean voice = answer.answerSource() != null
                && answer.answerSource().name().contains("VOICE");
        return voice ? "kiosk intake (voice)" : "kiosk intake";
    }

    private static String slug(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
    }
}