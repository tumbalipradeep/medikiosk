package in.devmedi.kiosk.module.clinical.dialogue;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClinicalConversationResultTests {

    private final ClinicalConversationResult result = new ClinicalConversationResult();

    private static ClinicalAnswer answer(String questionId, String section, String type, String text) {
        return new ClinicalAnswer(questionId, section, type, text, "patient said " + questionId);
    }

    @Test
    void recordedAnswersAreKeptInOrder() {
        result.record(answer("chief_complaint_symptom", "CHIEF_COMPLAINT", "SYMPTOM_PROBLEM", "first"));
        result.record(answer("hpi_onset", "HISTORY_OF_PRESENT_ILLNESS", "ONSET", "second"));
        result.record(answer("hpi_provocation_palliation", "HISTORY_OF_PRESENT_ILLNESS", "PROVOCATION_PALLIATION", "third"));

        List<ClinicalAnswer> all = result.all();

        assertThat(result.size()).isEqualTo(3);
        assertThat(all).extracting(ClinicalAnswer::questionId)
                .containsExactly("chief_complaint_symptom", "hpi_onset", "hpi_provocation_palliation");
        assertThat(all.get(2).answer()).isEqualTo("patient said hpi_provocation_palliation");
    }

    @Test
    void resultIsEmptyBeforeAnyAnswer() {
        assertThat(result.isEmpty()).isTrue();
        assertThat(result.size()).isZero();
    }

    @Test
    void snapshotIsUnmodifiable() {
        result.record(answer("hpi_onset", "HISTORY_OF_PRESENT_ILLNESS", "ONSET", "x"));

        List<ClinicalAnswer> snapshot = result.all();

        assertThatThrownBy(() -> snapshot.add(answer("extra", "DASHAVIDHA", "PRAKRITI", "y")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void hpiDashavidhaAndAharaViharaAnswersCoexistInOneResult() {
        result.record(answer("chief_complaint_symptom", "CHIEF_COMPLAINT", "SYMPTOM_PROBLEM", "hpi"));
        result.record(answer("dashavidha_sara", "DASHAVIDHA", "SARA", "dashavidha"));
        result.record(answer("ahara_vihara_sleep", "AHARA_VIHARA", "SLEEP", "ahara_vihara"));

        List<ClinicalAnswer> all = result.all();

        assertThat(all).extracting(ClinicalAnswer::section)
                .containsExactly("CHIEF_COMPLAINT", "DASHAVIDHA", "AHARA_VIHARA");
        assertThat(all).extracting(ClinicalAnswer::questionType)
                .containsExactly("SYMPTOM_PROBLEM", "SARA", "SLEEP");
        assertThat(all).extracting(ClinicalAnswer::questionText)
                .containsExactly("hpi", "dashavidha", "ahara_vihara");
    }
}