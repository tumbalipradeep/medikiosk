package in.devmedi.kiosk.module.fhir.mapping;

import in.devmedi.kiosk.module.clinical.dialogue.ClinicalAnswer;
import in.devmedi.kiosk.module.clinical.dialogue.QuestionSource;
import in.devmedi.kiosk.module.fhir.id.FhirIds;
import in.devmedi.kiosk.module.fhir.model.FhirObservation;
import in.devmedi.kiosk.module.physician.entity.CompletedCaseAnswerEntity;
import in.devmedi.kiosk.module.physician.entity.CompletedCaseEntity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FhirAnswerMapperTest {

    private static final FhirContextRefs REFS =
            new FhirContextRefs("Patient/" + FhirIds.patient("patient7"),
                    "Encounter/" + FhirIds.encounter("case-1"));

    @Test
    void mapsQuestionAsCodeAndAnswerAsValueString() {
        CompletedCaseAnswerEntity answer = CompletedCaseAnswerEntity.from(
                new CompletedCaseEntity("case-1"), 0,
                new ClinicalAnswer("hpi_onset", "HISTORY_OF_PRESENT_ILLNESS", "ONSET",
                        "When did it first start?", "Three days ago"));

        FhirObservation observation = FhirAnswerMapper.toFhir(answer, "case-1", REFS);

        assertThat(observation.resourceType()).isEqualTo("Observation");
        assertThat(observation.id()).isEqualTo(FhirIds.answer("case-1", 0));
        assertThat(observation.status()).isEqualTo("final");
        assertThat(observation.code().coding()).extracting("system")
                .containsExactly("urn:medikiosk:question-code", "urn:medikiosk:section");
        assertThat(observation.code().coding().getFirst().code()).isEqualTo("hpi_onset");
        assertThat(observation.code().coding().getFirst().display())
                .isEqualTo("When did it first start?");
        assertThat(observation.code().text()).isEqualTo("When did it first start?");
        assertThat(observation.subject().reference()).isEqualTo(REFS.subjectReference());
        assertThat(observation.encounter().reference()).isEqualTo(REFS.encounterReference());
        assertThat(observation.valueString()).isEqualTo("Three days ago");
        assertThat(observation.valueQuantity()).isNull();
        assertThat(observation.note()).hasSize(1);
        assertThat(observation.note().getFirst().text()).contains("answerSource=TEXT",
                "questionSource=DETERMINISTIC", "language=en-IN");
    }

    @Test
    void preservesAiRewordedDisplayedQuestionAsCodeText() {
        CompletedCaseAnswerEntity answer = CompletedCaseAnswerEntity.from(
                new CompletedCaseEntity("case-1"), 0,
                new ClinicalAnswer("hpi_onset", "HISTORY_OF_PRESENT_ILLNESS", "ONSET",
                        "When did it first start?", "Could you tell me when the discomfort began?",
                        "Three days ago", QuestionSource.AI_GENERATED));

        FhirObservation observation = FhirAnswerMapper.toFhir(answer, "case-1", REFS);

        assertThat(observation.code().coding().getFirst().display())
                .isEqualTo("When did it first start?");
        assertThat(observation.code().text()).isEqualTo("Could you tell me when the discomfort began?");
        assertThat(observation.note().getFirst().text())
                .contains("questionSource=AI_GENERATED");
    }

    @Test
    void blankAnswerProducesNoValueElement() {
        CompletedCaseAnswerEntity answer = CompletedCaseAnswerEntity.from(
                new CompletedCaseEntity("case-1"), 1,
                new ClinicalAnswer("hpv_events", "HISTORY_OF_PRESENT_ILLNESS", "EVENTS",
                        "Any prior episodes?", " "));

        FhirObservation observation = FhirAnswerMapper.toFhir(answer, "case-1", REFS);

        assertThat(observation.valueString()).isNull();
        assertThat(observation.valueQuantity()).isNull();
    }
}