package in.devmedi.kiosk.module.fhir.mapping;

import in.devmedi.kiosk.module.fhir.id.FhirIds;
import in.devmedi.kiosk.module.fhir.model.FhirEncounter;
import in.devmedi.kiosk.module.fhir.model.FhirReference;
import in.devmedi.kiosk.module.physician.entity.CompletedCaseEntity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FhirEncounterMapperTest {

    @Test
    void mapsFinishedAmbulatoryEncounterWithSubject() {
        CompletedCaseEntity completedCase = new CompletedCaseEntity("case-42");
        FhirReference subject =
                FhirReference.of("Patient/" + FhirIds.patient("patient7"), "Priya Sharma");

        FhirEncounter encounter = FhirEncounterMapper.toFhir(completedCase, subject);

        assertThat(encounter.resourceType()).isEqualTo("Encounter");
        assertThat(encounter.id()).isEqualTo(FhirIds.encounter("case-42"));
        assertThat(encounter.status()).isEqualTo("finished");
        assertThat(encounter.clazz().coding()).hasSize(1);
        assertThat(encounter.clazz().coding().getFirst().system())
                .isEqualTo("http://terminology.hl7.org/CodeSystem/v3-ActCode");
        assertThat(encounter.clazz().coding().getFirst().code()).isEqualTo("AMB");
        assertThat(encounter.subject().reference()).isEqualTo("Patient/" + FhirIds.patient("patient7"));
        assertThat(encounter.subject().display()).isEqualTo("Priya Sharma");
    }

    @Test
    void noPeriodWhenCaseHasNoPersistedCreationTime() {
        CompletedCaseEntity completedCase = new CompletedCaseEntity("case-42");

        FhirEncounter encounter = FhirEncounterMapper.toFhir(completedCase, null);

        assertThat(encounter.subject()).isNull();
        assertThat(encounter.period()).isNull();
    }
}