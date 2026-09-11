package in.devmedi.kiosk.module.fhir.mapping;

import in.devmedi.kiosk.module.auth.entity.Role;
import in.devmedi.kiosk.module.auth.entity.User;
import in.devmedi.kiosk.module.fhir.id.FhirIds;
import in.devmedi.kiosk.module.fhir.model.FhirPatient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FhirPatientMapperTest {

    @Test
    void mapsUsernameIdentifierAndDisplayName() {
        User user = new User("patient7", "secret", "Priya Sharma", Role.PATIENT);

        FhirPatient patient = FhirPatientMapper.toFhir(user);

        assertThat(patient.resourceType()).isEqualTo("Patient");
        assertThat(patient.id()).isEqualTo(FhirIds.patient("patient7"));
        assertThat(patient.active()).isTrue();
        assertThat(patient.identifier()).hasSize(1);
        assertThat(patient.identifier().getFirst().system()).isEqualTo("urn:medikiosk:username");
        assertThat(patient.identifier().getFirst().value()).isEqualTo("patient7");
        assertThat(patient.name()).hasSize(1);
        assertThat(patient.name().getFirst().text()).isEqualTo("Priya Sharma");
    }

    @Test
    void neverEmitsDemographicsThatAreNotPersisted() {
        User user = new User("patient7", "secret", "Priya Sharma", Role.PATIENT);

        FhirPatient patient = FhirPatientMapper.toFhir(user);

        // The record declares no demographic components; absence is also asserted in JSON shape.
        assertThat(patient.toString()).doesNotContain("gender").doesNotContain("birthDate");
    }

    @Test
    void disabledAccountIsNotActive() {
        User user = new User("patient7", "secret", "Priya Sharma", Role.PATIENT);
        user.setEnabled(false);

        assertThat(FhirPatientMapper.toFhir(user).active()).isFalse();
    }
}