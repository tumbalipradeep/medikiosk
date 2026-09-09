package in.devmedi.kiosk.module.clinical.summary;

import in.devmedi.kiosk.module.clinical.ayush.AharaViharaQuestion;
import in.devmedi.kiosk.module.clinical.ayush.AharaViharaQuestionPlanner;
import in.devmedi.kiosk.module.clinical.ayush.DashavidhaQuestion;
import in.devmedi.kiosk.module.clinical.ayush.DashavidhaQuestionPlanner;
import in.devmedi.kiosk.module.clinical.dialogue.ClinicalAnswer;
import in.devmedi.kiosk.module.clinical.dialogue.ClinicalConversationResult;
import in.devmedi.kiosk.module.clinical.dialogue.ClinicalQuestion;
import in.devmedi.kiosk.module.clinical.dialogue.IntakeQuestion;
import in.devmedi.kiosk.module.clinical.dialogue.QuestionPlanner;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClinicalSummaryBuilderTests {

    private final ClinicalSummaryBuilder builder = new ClinicalSummaryBuilder();
    private final QuestionPlanner questionPlanner = new QuestionPlanner();
    private final DashavidhaQuestionPlanner dashavidhaPlanner = new DashavidhaQuestionPlanner();
    private final AharaViharaQuestionPlanner aharaViharaPlanner = new AharaViharaQuestionPlanner();

    private void recordClinicalAnswers(ClinicalConversationResult result, List<ClinicalQuestion> questions) {
        for (ClinicalQuestion question : questions) {
            result.record(ClinicalAnswer.from(IntakeQuestion.fromClinical(question), "answer-" + question.id()));
        }
    }

    private void recordDashavidhaAnswers(ClinicalConversationResult result, List<DashavidhaQuestion> questions) {
        for (DashavidhaQuestion question : questions) {
            result.record(ClinicalAnswer.from(IntakeQuestion.fromDashavidha(question), "answer-" + question.id()));
        }
    }

    private void recordAharaViharaAnswers(ClinicalConversationResult result, List<AharaViharaQuestion> questions) {
        for (AharaViharaQuestion question : questions) {
            result.record(ClinicalAnswer.from(IntakeQuestion.fromAharaVihara(question), "answer-" + question.id()));
        }
    }

    private ClinicalSummarySection section(ClinicalSummary summary, String name) {
        return summary.sections().stream()
                .filter(s -> s.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void completeHpiSummaryIsProducedInOrderWithContextPreserved() {
        ClinicalConversationResult result = new ClinicalConversationResult();
        recordClinicalAnswers(result, questionPlanner.questions());

        ClinicalSummary summary = builder.summarize(result);
        ClinicalSummarySection hpi = section(summary, ClinicalSummaryBuilder.HPI_SECTION_NAME);

        assertThat(hpi.entries()).hasSize(7);
        assertThat(hpi.entries()).extracting(entry -> entry.questionId())
                .containsExactly(
                        "chief_complaint_symptom", "hpi_onset", "hpi_provocation_palliation", "hpi_quality",
                        "hpi_region_radiation", "hpi_severity", "hpi_timing_duration");
        assertThat(hpi.entries()).extracting(ClinicalSummaryEntry::questionType)
                .containsExactly(
                        "SYMPTOM_PROBLEM", "ONSET", "PROVOCATION_PALLIATION", "QUALITY",
                        "REGION_RADIATION", "SEVERITY", "TIMING_DURATION");
        for (int i = 0; i < hpi.entries().size(); i++) {
            ClinicalQuestion source = questionPlanner.questions().get(i);
            ClinicalSummaryEntry entry = hpi.entries().get(i);
            assertThat(entry.questionText()).isEqualTo(source.text());
            assertThat(entry.answer()).isEqualTo("answer-" + source.id());
        }
    }

    @Test
    void allTenDashavidhaParametersAreSummarizedInOrder() {
        ClinicalConversationResult result = new ClinicalConversationResult();
        recordDashavidhaAnswers(result, dashavidhaPlanner.questions());

        ClinicalSummary summary = builder.summarize(result);
        ClinicalSummarySection dashavidha = section(summary, ClinicalSummaryBuilder.DASHAVIDHA_SECTION_NAME);

        assertThat(dashavidha.entries()).hasSize(10);
        assertThat(dashavidha.entries()).extracting(ClinicalSummaryEntry::questionType)
                .containsExactly(
                        "PRAKRITI", "VIKRITI", "SARA", "SAMHANANA", "PRAMANA",
                        "SATMYA", "SATTVA", "AHARA_SHAKTI", "VYAYAMA_SHAKTI", "VAYA");
        for (int i = 0; i < dashavidha.entries().size(); i++) {
            DashavidhaQuestion source = dashavidhaPlanner.questions().get(i);
            assertThat(dashavidha.entries().get(i).questionText()).isEqualTo(source.text());
            assertThat(dashavidha.entries().get(i).answer()).isEqualTo("answer-" + source.id());
        }
    }

    @Test
    void allEightAharaViharaParametersAreSummarizedInOrder() {
        ClinicalConversationResult result = new ClinicalConversationResult();
        recordAharaViharaAnswers(result, aharaViharaPlanner.questions());

        ClinicalSummary summary = builder.summarize(result);
        ClinicalSummarySection aharaVihara = section(summary, ClinicalSummaryBuilder.AHARA_VIHARA_SECTION_NAME);

        assertThat(aharaVihara.entries()).hasSize(8);
        assertThat(aharaVihara.entries()).extracting(ClinicalSummaryEntry::questionType)
                .containsExactly(
                        "AHARA", "MEAL_PATTERN", "APPETITE", "HYDRATION",
                        "SLEEP", "PHYSICAL_ACTIVITY", "DAILY_ROUTINE", "HABITS");
        for (int i = 0; i < aharaVihara.entries().size(); i++) {
            AharaViharaQuestion source = aharaViharaPlanner.questions().get(i);
            assertThat(aharaVihara.entries().get(i).questionText()).isEqualTo(source.text());
            assertThat(aharaVihara.entries().get(i).answer()).isEqualTo("answer-" + source.id());
        }
    }

    @Test
    void fullJourneyPreservesDeterministicSectionAndEntryOrder() {
        ClinicalConversationResult result = new ClinicalConversationResult();
        recordClinicalAnswers(result, questionPlanner.questions());
        recordDashavidhaAnswers(result, dashavidhaPlanner.questions());
        recordAharaViharaAnswers(result, aharaViharaPlanner.questions());

        ClinicalSummary summary = builder.summarize(result);

        assertThat(summary.sections()).extracting(ClinicalSummarySection::name)
                .containsExactly(
                        ClinicalSummaryBuilder.HPI_SECTION_NAME,
                        ClinicalSummaryBuilder.DASHAVIDHA_SECTION_NAME,
                        ClinicalSummaryBuilder.AHARA_VIHARA_SECTION_NAME);
        assertThat(summary.sections().get(0).entries()).hasSize(7);
        assertThat(summary.sections().get(1).entries()).hasSize(10);
        assertThat(summary.sections().get(2).entries()).hasSize(8);
        assertThat(summary.answeredCount()).isEqualTo(25);
        assertThat(summary.entryCount()).isEqualTo(25);

        List<String> flattened = summary.sections().stream()
                .flatMap(s -> s.entries().stream())
                .map(ClinicalSummaryEntry::questionId)
                .toList();
        assertThat(flattened).containsExactlyElementsOf(expectedJourneyIds());
    }

    @Test
    void emptyResultProducesCanonicalEmptySections() {
        ClinicalSummary summary = builder.summarize(new ClinicalConversationResult());

        assertThat(summary.sections()).hasSize(3);
        assertThat(summary.sections()).allMatch(ClinicalSummarySection::isEmpty);
        assertThat(summary.sections()).extracting(ClinicalSummarySection::name)
                .containsExactly(
                        ClinicalSummaryBuilder.HPI_SECTION_NAME,
                        ClinicalSummaryBuilder.DASHAVIDHA_SECTION_NAME,
                        ClinicalSummaryBuilder.AHARA_VIHARA_SECTION_NAME);
        assertThat(summary.answeredCount()).isZero();
        assertThat(summary.entryCount()).isZero();
    }

    @Test
    void nullResultIsHandledSafelyAsAnEmptyConversation() {
        ClinicalSummary summary = builder.summarize(null);

        assertThat(summary.sections()).hasSize(3);
        assertThat(summary.sections()).allMatch(ClinicalSummarySection::isEmpty);
        assertThat(summary.answeredCount()).isZero();
    }

    @Test
    void incompleteResultOnlyPopulatesTheAnsweredSections() {
        ClinicalConversationResult result = new ClinicalConversationResult();
        recordClinicalAnswers(result, questionPlanner.questions().subList(0, 3));

        ClinicalSummary summary = builder.summarize(result);

        ClinicalSummarySection hpi = section(summary, ClinicalSummaryBuilder.HPI_SECTION_NAME);
        assertThat(hpi.entries()).hasSize(3);
        assertThat(hpi.entries()).extracting(ClinicalSummaryEntry::questionId)
                .containsExactly("chief_complaint_symptom", "hpi_onset", "hpi_provocation_palliation");
        assertThat(summary.answeredCount()).isEqualTo(3);

        ClinicalSummarySection dashavidha = summary.sections().get(1);
        ClinicalSummarySection aharaVihara = summary.sections().get(2);
        assertThat(dashavidha.isEmpty()).isTrue();
        assertThat(aharaVihara.isEmpty()).isTrue();
    }

    @Test
    void noCapturedAnswerIsLostOrDuplicated() {
        ClinicalConversationResult result = new ClinicalConversationResult();
        recordClinicalAnswers(result, questionPlanner.questions());
        recordDashavidhaAnswers(result, dashavidhaPlanner.questions());
        recordAharaViharaAnswers(result, aharaViharaPlanner.questions());

        ClinicalSummary summary = builder.summarize(result);

        List<String> captured = result.all().stream().map(ClinicalAnswer::questionId).toList();
        List<String> summarized = summary.sections().stream()
                .flatMap(s -> s.entries().stream())
                .map(ClinicalSummaryEntry::questionId)
                .toList();

        assertThat(summarized).containsExactlyElementsOf(captured);
        assertThat(summarized).doesNotHaveDuplicates();
        assertThat(summary.answeredCount()).isEqualTo(result.size());
        assertThat(summary.entryCount()).isEqualTo(result.size());
    }

    private List<String> expectedJourneyIds() {
        List<String> ids = new ArrayList<>();
        questionPlanner.questions().forEach(q -> ids.add(q.id()));
        dashavidhaPlanner.questions().forEach(q -> ids.add(q.id()));
        aharaViharaPlanner.questions().forEach(q -> ids.add(q.id()));
        return ids;
    }
}