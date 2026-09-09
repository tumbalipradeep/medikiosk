package in.devmedi.kiosk.module.clinical.ayush;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DashavidhaQuestionPlannerTests {

    private final DashavidhaQuestionPlanner planner = new DashavidhaQuestionPlanner();

    @Test
    void firstQuestionStartsTheDashavidhaSequence() {
        DashavidhaQuestion first = planner.firstQuestion();

        assertThat(first.id()).isEqualTo("dashavidha_prakriti");
        assertThat(first.parameter()).isEqualTo(DashavidhaParameter.PRAKRITI);
        assertThat(first.text()).isNotBlank();
        assertThat(first.order()).isEqualTo(1);
    }

    @Test
    void allTenParametersArePresent() {
        List<DashavidhaParameter> parameters = planner.questions().stream()
                .map(DashavidhaQuestion::parameter)
                .toList();

        assertThat(parameters).containsExactly(
                DashavidhaParameter.PRAKRITI,
                DashavidhaParameter.VIKRITI,
                DashavidhaParameter.SARA,
                DashavidhaParameter.SAMHANANA,
                DashavidhaParameter.PRAMANA,
                DashavidhaParameter.SATMYA,
                DashavidhaParameter.SATTVA,
                DashavidhaParameter.AHARA_SHAKTI,
                DashavidhaParameter.VYAYAMA_SHAKTI,
                DashavidhaParameter.VAYA);
    }

    @Test
    void deterministicOrderingUsesStableIdsAndOrderField() {
        List<DashavidhaQuestion> questions = planner.questions();

        assertThat(questions).extracting(DashavidhaQuestion::order)
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        assertThat(questions).extracting(DashavidhaQuestion::id)
                .containsExactly(
                        "dashavidha_prakriti",
                        "dashavidha_vikriti",
                        "dashavidha_sara",
                        "dashavidha_samhanana",
                        "dashavidha_pramana",
                        "dashavidha_satmya",
                        "dashavidha_sattva",
                        "dashavidha_ahara_shakti",
                        "dashavidha_vyayama_shakti",
                        "dashavidha_vaya");
    }

    @Test
    void advancingAfterAnAnswerReturnsTheNextQuestion() {
        assertThat(planner.nextQuestion("dashavidha_prakriti"))
                .hasValueSatisfying(q -> {
                    assertThat(q.id()).isEqualTo("dashavidha_vikriti");
                    assertThat(q.parameter()).isEqualTo(DashavidhaParameter.VIKRITI);
                });
        assertThat(planner.nextQuestion("dashavidha_samhanana"))
                .hasValueSatisfying(q -> assertThat(q.parameter()).isEqualTo(DashavidhaParameter.PRAMANA));
        assertThat(planner.nextQuestion(planner.firstQuestion()))
                .hasValueSatisfying(q -> assertThat(q.parameter()).isEqualTo(DashavidhaParameter.VIKRITI));
    }

    @Test
    void completionHappensAfterParameterTen() {
        DashavidhaQuestion last = planner.questions().get(planner.questions().size() - 1);

        assertThat(last.parameter()).isEqualTo(DashavidhaParameter.VAYA);
        assertThat(last.order()).isEqualTo(10);
        assertThat(planner.nextQuestion(last)).isEmpty();
        assertThat(planner.nextQuestion(last.id())).isEmpty();
    }

    @Test
    void noQuestionIsReturnedAfterCompletion() {
        DashavidhaQuestion last = planner.questions().get(planner.questions().size() - 1);

        assertThat(planner.nextQuestion(last)).isEmpty();
        assertThat(planner.nextQuestion(last.id())).isEmpty();
    }

    @Test
    void unknownQuestionIdIsRejected() {
        assertThatThrownBy(() -> planner.nextQuestion("does_not_exist"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does_not_exist");
    }

    @Test
    void questionByIdAccessorReturnsTheAndExactlyOneQuestion() {
        DashavidhaQuestion question = planner.question("dashavidha_sara");

        assertThat(question.parameter()).isEqualTo(DashavidhaParameter.SARA);
        assertThat(question.order()).isEqualTo(3);
        assertThat(planner.question("dashavidha_vaya").parameter()).isEqualTo(DashavidhaParameter.VAYA);
    }

    @Test
    void requiredAndOptionalMetadataIsSet() {
        List<DashavidhaQuestion> questions = planner.questions();

        assertThat(questions).extracting(DashavidhaQuestion::required)
                .containsExactly(true, true, true, true, false, true, true, true, true, true);

        assertThat(questions)
                .filteredOn(q -> !q.required())
                .extracting(DashavidhaQuestion::parameter)
                .containsExactly(DashavidhaParameter.PRAMANA);
    }

    @Test
    void theWholeSequenceWalksToCompletionDeterministically() {
        List<String> seen = new ArrayList<>();
        DashavidhaQuestion current = planner.firstQuestion();
        seen.add(current.parameter().name());
        while (planner.nextQuestion(current).isPresent()) {
            current = planner.nextQuestion(current).orElseThrow();
            seen.add(current.parameter().name());
        }

        assertThat(seen).containsExactly(
                "PRAKRITI", "VIKRITI", "SARA", "SAMHANANA", "PRAMANA",
                "SATMYA", "SATTVA", "AHARA_SHAKTI", "VYAYAMA_SHAKTI", "VAYA");
        assertThat(seen).hasSize(10);
    }
}