package in.devmedi.kiosk.module.clinical.dialogue;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuestionPlannerTests {

    private final QuestionPlanner planner = new QuestionPlanner();

    @Test
    void initialQuestionIsTheChiefComplaintSymptom() {
        ClinicalQuestion first = planner.firstQuestion();

        assertThat(first.id()).isEqualTo("chief_complaint_symptom");
        assertThat(first.section()).isEqualTo(ClinicalSection.CHIEF_COMPLAINT);
        assertThat(first.type()).isEqualTo(QuestionType.SYMPTOM_PROBLEM);
        assertThat(first.text()).isNotBlank();
        assertThat(first.required()).isTrue();
        assertThat(first.order()).isEqualTo(1);
    }

    @Test
    void advancingAfterAnAnswerFollowsTheFixedSequence() {
        List<String> seen = new ArrayList<>();
        ClinicalQuestion current = planner.firstQuestion();
        seen.add(current.id());

        while (planner.nextQuestion(current).isPresent()) {
            current = planner.nextQuestion(current).orElseThrow();
            seen.add(current.id());
        }

        assertThat(seen).containsExactly(
                "chief_complaint_symptom",
                "hpi_onset",
                "hpi_provocation_palliation",
                "hpi_quality",
                "hpi_region_radiation",
                "hpi_severity",
                "hpi_timing_duration");
    }

    @Test
    void questionOrderingMatchesSocratesFacets() {
        List<ClinicalQuestion> questions = planner.questions();

        assertThat(questions).extracting(ClinicalQuestion::order)
                .containsExactly(1, 2, 3, 4, 5, 6, 7);
        assertThat(questions).extracting(ClinicalQuestion::type)
                .containsExactly(
                        QuestionType.SYMPTOM_PROBLEM,
                        QuestionType.ONSET,
                        QuestionType.PROVOCATION_PALLIATION,
                        QuestionType.QUALITY,
                        QuestionType.REGION_RADIATION,
                        QuestionType.SEVERITY,
                        QuestionType.TIMING_DURATION);
        assertThat(questions).extracting(ClinicalQuestion::section)
                .containsExactly(
                        ClinicalSection.CHIEF_COMPLAINT,
                        ClinicalSection.HISTORY_OF_PRESENT_ILLNESS,
                        ClinicalSection.HISTORY_OF_PRESENT_ILLNESS,
                        ClinicalSection.HISTORY_OF_PRESENT_ILLNESS,
                        ClinicalSection.HISTORY_OF_PRESENT_ILLNESS,
                        ClinicalSection.HISTORY_OF_PRESENT_ILLNESS,
                        ClinicalSection.HISTORY_OF_PRESENT_ILLNESS);
    }

    @Test
    void hpiSequenceCompletesAfterTheFinalQuestion() {
        ClinicalQuestion last = planner.questions().get(planner.questions().size() - 1);

        assertThat(last.id()).isEqualTo("hpi_timing_duration");
        assertThat(last.section()).isEqualTo(ClinicalSection.HISTORY_OF_PRESENT_ILLNESS);
        assertThat(last.type()).isEqualTo(QuestionType.TIMING_DURATION);
        assertThat(planner.nextQuestion(last)).isEmpty();
        assertThat(planner.nextQuestion(last.id())).isEmpty();
        assertThat(planner.state(last.id())).isEqualTo(DialogueState.COMPLETED);
    }

    @Test
    void noQuestionReturnedAfterCompletion() {
        ClinicalQuestion last = planner.questions().get(planner.questions().size() - 1);

        Optional<ClinicalQuestion> next = planner.nextQuestion(last);

        assertThat(next).isEmpty();
        assertThat(planner.state(last.id())).isEqualTo(DialogueState.COMPLETED);
    }

    @Test
    void stateTransitionsFromNotStartedToInProgressToCompleted() {
        assertThat(planner.state(null)).isEqualTo(DialogueState.NOT_STARTED);
        assertThat(planner.state("  ")).isEqualTo(DialogueState.NOT_STARTED);

        ClinicalQuestion middle = planner.questions().get(3);
        assertThat(planner.state(middle.id())).isEqualTo(DialogueState.IN_PROGRESS);

        ClinicalQuestion last = planner.questions().get(planner.questions().size() - 1);
        assertThat(planner.state(last.id())).isEqualTo(DialogueState.COMPLETED);
    }

    @Test
    void plannerIsDeterministic() {
        ClinicalQuestion firstCall = planner.firstQuestion();
        ClinicalQuestion secondCall = planner.firstQuestion();
        assertThat(firstCall).isEqualTo(secondCall);
        assertThat(firstCall.hashCode()).isEqualTo(secondCall.hashCode());

        assertThat(planner.nextQuestion("hpi_onset"))
                .hasValueSatisfying(q -> assertThat(q.id()).isEqualTo("hpi_provocation_palliation"));
        assertThat(planner.nextQuestion("hpi_onset"))
                .hasValueSatisfying(q -> assertThat(q.id()).isEqualTo("hpi_provocation_palliation"));
    }

    @Test
    void requiredAndOptionalFlagsAreSet() {
        assertThat(planner.questions())
                .filteredOn(q -> q.type() == QuestionType.SEVERITY)
                .extracting(ClinicalQuestion::required)
                .containsExactly(false);
        assertThat(planner.questions())
                .filteredOn(q -> !q.required())
                .extracting(ClinicalQuestion::type)
                .containsExactly(QuestionType.SEVERITY);
    }

    @Test
    void unknownQuestionIdIsRejected() {
        assertThatThrownBy(() -> planner.nextQuestion("does_not_exist"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does_not_exist");
        assertThatThrownBy(() -> planner.state("does_not_exist"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}