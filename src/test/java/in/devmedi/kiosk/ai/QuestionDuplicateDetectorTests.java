package in.devmedi.kiosk.ai;

import in.devmedi.kiosk.module.ai.conversation.QuestionDuplicateDetector;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionDuplicateDetectorTests {

    private final QuestionDuplicateDetector detector = new QuestionDuplicateDetector();

    @Test
    void identicalWordingIsDuplicate() {
        assertThat(detector.isDuplicate("When did it first start?",
                List.of("When did it first start?"))).isTrue();
    }

    @Test
    void nearIdenticalWordingIsDuplicate() {
        assertThat(detector.isDuplicate("When exactly did it first start?",
                List.of("When did it first start?"))).isTrue();
    }

    @Test
    void subsetWordingOfLongerQuestionIsDuplicate() {
        assertThat(detector.isDuplicate("How long?",
                List.of("How long does each episode last?"))).isTrue();
    }

    @Test
    void differentObjectiveIsNotDuplicate() {
        assertThat(detector.isDuplicate("What makes it better or worse?",
                List.of("When did it first start?"))).isFalse();
    }

    @Test
    void creativeRephraseIsNotFlaggedWhenWordsDiffer() {
        assertThat(detector.isDuplicate("Tell me about when this began for you",
                List.of("How long does each episode last?"))).isFalse();
    }

    @Test
    void emptyCandidateOrAskedListIsNeverDuplicate() {
        assertThat(detector.isDuplicate("", List.of("When did it start?"))).isFalse();
        assertThat(detector.isDuplicate(null, List.of("When did it start?"))).isFalse();
        assertThat(detector.isDuplicate("When did it start?", List.of())).isFalse();
        assertThat(detector.isDuplicate("When did it start?", null)).isFalse();
    }

    @Test
    void nullAskedEntriesAreIgnored() {
        assertThat(detector.isDuplicate("When did it start?",
                java.util.Arrays.asList("When did it start?", null))).isTrue();
    }
}