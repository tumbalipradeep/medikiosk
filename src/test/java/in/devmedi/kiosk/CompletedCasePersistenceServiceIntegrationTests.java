package in.devmedi.kiosk;

import in.devmedi.kiosk.module.auth.repository.UserRepository;
import in.devmedi.kiosk.module.clinical.dialogue.ClinicalAnswer;
import in.devmedi.kiosk.module.clinical.dialogue.ClinicalConversationResult;
import in.devmedi.kiosk.module.physician.service.CompletedCase;
import in.devmedi.kiosk.module.physician.service.CompletedCasePersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CompletedCasePersistenceServiceIntegrationTests {

    @Autowired
    private CompletedCasePersistenceService persistence;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void clearPersistedData() {
        persistence.deleteAll();
    }

    private ClinicalConversationResult buildResult(int answerCount) {
        ClinicalConversationResult result = new ClinicalConversationResult();
        for (int i = 0; i < answerCount; i++) {
            result.record(new ClinicalAnswer(
                    "question_" + i, "HISTORY_OF_PRESENT_ILLNESS", "SOCRATES",
                    "Question text " + i, "Answer " + i));
        }
        return result;
    }

    @Test
    void saveAndRetrieveByCaseId() {
        CompletedCase completed = CompletedCase.withNewId(buildResult(5));
        persistence.save(completed);

        CompletedCase retrieved = persistence.findByCaseId(completed.id()).orElseThrow();
        assertThat(retrieved.id()).isEqualTo(completed.id());
        assertThat(retrieved.result().size()).isEqualTo(5);
    }

    @Test
    void stableCaseIdIsUniqueAcrossMultipleSavedCases() {
        CompletedCase first = CompletedCase.withNewId(buildResult(3));
        CompletedCase second = CompletedCase.withNewId(buildResult(4));
        persistence.save(first);
        persistence.save(second);

        CompletedCase loadedFirst = persistence.findByCaseId(first.id()).orElseThrow();
        CompletedCase loadedSecond = persistence.findByCaseId(second.id()).orElseThrow();

        assertThat(loadedFirst.id()).isNotEqualTo(loadedSecond.id());
        assertThat(loadedFirst.result().size()).isEqualTo(3);
        assertThat(loadedSecond.result().size()).isEqualTo(4);
    }

    @Test
    void allCapturedAnswersSurvivePersistenceInOrder() {
        ClinicalConversationResult result = buildResult(8);
        CompletedCase completed = CompletedCase.withNewId(result);
        persistence.save(completed);

        CompletedCase loaded = persistence.findByCaseId(completed.id()).orElseThrow();
        assertThat(loaded.result().all())
                .extracting(ClinicalAnswer::answer)
                .containsExactly("Answer 0", "Answer 1", "Answer 2", "Answer 3",
                        "Answer 4", "Answer 5", "Answer 6", "Answer 7");
    }

    @Test
    void questionFieldsArePreservedVerbatim() {
        ClinicalConversationResult result = new ClinicalConversationResult();
        result.record(new ClinicalAnswer("chief_complaint_symptom", "CHIEF_COMPLAINT",
                "SYMPTOM", "Describe your main problem", "Throbbing headache"));
        CompletedCase completed = CompletedCase.withNewId(result);
        persistence.save(completed);

        ClinicalAnswer loaded = persistence.findByCaseId(completed.id()).orElseThrow()
                .result().all().getFirst();
        assertThat(loaded.questionId()).isEqualTo("chief_complaint_symptom");
        assertThat(loaded.section()).isEqualTo("CHIEF_COMPLAINT");
        assertThat(loaded.questionType()).isEqualTo("SYMPTOM");
        assertThat(loaded.questionText()).isEqualTo("Describe your main problem");
        assertThat(loaded.answer()).isEqualTo("Throbbing headache");
    }

    @Test
    void findLatestReturnsMostRecentlySavedCase() {
        CompletedCase first = CompletedCase.withNewId(buildResult(2));
        persistence.save(first);

        CompletedCase second = CompletedCase.withNewId(buildResult(3));
        persistence.save(second);

        CompletedCase latest = persistence.findLatest().orElseThrow();
        assertThat(latest.id()).isEqualTo(second.id());
        assertThat(latest.result().size()).isEqualTo(3);
    }

    @Test
    void findLatestReturnsEmptyWhenNoCasesPersisted() {
        assertThat(persistence.findLatest()).isEmpty();
    }

    @Test
    void findByIdReturnsEmptyForNonExistentCase() {
        assertThat(persistence.findByCaseId("case-non-existent")).isEmpty();
    }

    @Test
    void replacingACaseUpdatesItsAnswersInPlace() {
        CompletedCase original = CompletedCase.withNewId(buildResult(3));
        persistence.save(original);

        ClinicalConversationResult updatedResult = buildResult(2);
        CompletedCase replaced = new CompletedCase(original.id(), updatedResult, null);
        persistence.save(replaced);

        CompletedCase loaded = persistence.findByCaseId(original.id()).orElseThrow();
        assertThat(loaded.id()).isEqualTo(original.id());
        assertThat(loaded.result().size()).isEqualTo(2);
    }

    @Test
    void clearRemovesAllPersistedCases() {
        persistence.save(CompletedCase.withNewId(buildResult(5)));
        persistence.save(CompletedCase.withNewId(buildResult(3)));

        persistence.deleteAll();

        assertThat(persistence.findLatest()).isEmpty();
    }

    @Test
    void ownerUserIdIsPersistedAndReloaded() {
        Long userId = userRepository.findByUsername("patient").orElseThrow().getId();
        CompletedCase completed = CompletedCase.withNewId(buildResult(2), userId);
        persistence.save(completed, userId);

        CompletedCase loaded = persistence.findByCaseId(completed.id()).orElseThrow();
        assertThat(loaded.userId()).isEqualTo(userId);
    }

    @Test
    void caseWithoutOwnerKeepsUserIdNull() {
        CompletedCase completed = CompletedCase.withNewId(buildResult(2));
        persistence.save(completed);

        CompletedCase loaded = persistence.findByCaseId(completed.id()).orElseThrow();
        assertThat(loaded.userId()).isNull();
    }
}