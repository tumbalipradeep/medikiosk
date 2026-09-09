package in.devmedi.kiosk.module.clinical.ayush;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AharaViharaQuestionPlannerTests {

    private final AharaViharaQuestionPlanner planner = new AharaViharaQuestionPlanner();

    @Test
    void firstQuestionStartsTheAharaViharaSequence() {
        AharaViharaQuestion first = planner.firstQuestion();

        assertThat(first.id()).isEqualTo("ahara_vihara_ahara");
        assertThat(first.parameter()).isEqualTo(AharaViharaParameter.AHARA);
        assertThat(first.text()).isNotBlank();
        assertThat(first.order()).isEqualTo(1);
        assertThat(first.required()).isTrue();
    }

    @Test
    void allEightParametersArePresent() {
        List<AharaViharaParameter> parameters = planner.questions().stream()
                .map(AharaViharaQuestion::parameter)
                .toList();

        assertThat(parameters).containsExactly(
                AharaViharaParameter.AHARA,
                AharaViharaParameter.MEAL_PATTERN,
                AharaViharaParameter.APPETITE,
                AharaViharaParameter.HYDRATION,
                AharaViharaParameter.SLEEP,
                AharaViharaParameter.PHYSICAL_ACTIVITY,
                AharaViharaParameter.DAILY_ROUTINE,
                AharaViharaParameter.HABITS);
    }

    @Test
    void deterministicOrderingUsesStableIdsAndOrderField() {
        List<AharaViharaQuestion> questions = planner.questions();

        assertThat(questions).extracting(AharaViharaQuestion::order)
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
        assertThat(questions).extracting(AharaViharaQuestion::id)
                .containsExactly(
                        "ahara_vihara_ahara",
                        "ahara_vihara_meal_pattern",
                        "ahara_vihara_appetite",
                        "ahara_vihara_hydration",
                        "ahara_vihara_sleep",
                        "ahara_vihara_physical_activity",
                        "ahara_vihara_daily_routine",
                        "ahara_vihara_habits");
    }

    @Test
    void advancingAfterAnAnswerReturnsTheNextQuestion() {
        assertThat(planner.nextQuestion("ahara_vihara_ahara"))
                .hasValueSatisfying(q -> {
                    assertThat(q.id()).isEqualTo("ahara_vihara_meal_pattern");
                    assertThat(q.parameter()).isEqualTo(AharaViharaParameter.MEAL_PATTERN);
                });
        assertThat(planner.nextQuestion("ahara_vihara_sleep"))
                .hasValueSatisfying(q -> assertThat(q.parameter()).isEqualTo(AharaViharaParameter.PHYSICAL_ACTIVITY));
        assertThat(planner.nextQuestion(planner.firstQuestion()))
                .hasValueSatisfying(q -> assertThat(q.parameter()).isEqualTo(AharaViharaParameter.MEAL_PATTERN));
    }

    @Test
    void completionHappensAfterParameterEight() {
        AharaViharaQuestion last = planner.questions().get(planner.questions().size() - 1);

        assertThat(last.parameter()).isEqualTo(AharaViharaParameter.HABITS);
        assertThat(last.order()).isEqualTo(8);
        assertThat(planner.nextQuestion(last)).isEmpty();
        assertThat(planner.nextQuestion(last.id())).isEmpty();
    }

    @Test
    void noQuestionIsReturnedAfterCompletion() {
        AharaViharaQuestion last = planner.questions().get(planner.questions().size() - 1);

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
    void allMainHistoryQuestionsAreRequired() {
        assertThat(planner.questions()).extracting(AharaViharaQuestion::required)
                .containsExactly(true, true, true, true, true, true, true, true);
    }

    @Test
    void questionByIdAccessorReturnsTheSingleQuestion() {
        AharaViharaQuestion question = planner.question("ahara_vihara_sleep");

        assertThat(question.parameter()).isEqualTo(AharaViharaParameter.SLEEP);
        assertThat(question.order()).isEqualTo(5);
        assertThat(planner.question("ahara_vihara_habits").parameter()).isEqualTo(AharaViharaParameter.HABITS);
    }

    @Test
    void theWholeSequenceWalksToCompletionDeterministically() {
        List<String> seen = new ArrayList<>();
        AharaViharaQuestion current = planner.firstQuestion();
        seen.add(current.parameter().name());
        while (planner.nextQuestion(current).isPresent()) {
            current = planner.nextQuestion(current).orElseThrow();
            seen.add(current.parameter().name());
        }

        assertThat(seen).containsExactly(
                "AHARA", "MEAL_PATTERN", "APPETITE", "HYDRATION",
                "SLEEP", "PHYSICAL_ACTIVITY", "DAILY_ROUTINE", "HABITS");
        assertThat(seen).hasSize(8);
    }
}