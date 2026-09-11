package in.devmedi.kiosk.module.fhir.json;

import in.devmedi.kiosk.module.auth.entity.Role;
import in.devmedi.kiosk.module.auth.entity.User;
import in.devmedi.kiosk.module.fhir.mapping.FhirConsentMapper;
import in.devmedi.kiosk.module.fhir.mapping.FhirPatientMapper;
import in.devmedi.kiosk.module.fhir.model.FhirBundle;
import in.devmedi.kiosk.module.fhir.model.FhirEntry;
import in.devmedi.kiosk.module.consent.entity.Consent;
import in.devmedi.kiosk.module.consent.entity.ConsentType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FhirJsonTest {

    @Test
    void serializesEphemeralFieldsOmitted() {
        User user = new User("patient7", "secret", "Priya Sharma", Role.PATIENT);
        FhirEntry patientEntry = FhirEntry.of(
                "urn:uuid:" + in.devmedi.kiosk.module.fhir.id.FhirIds.patient("patient7"),
                FhirPatientMapper.toFhir(user));

        String json = FhirJson.toJson(patientEntry.resource());

        assertThat(json).contains("\"resourceType\":\"Patient\"");
        assertThat(json).contains("\"id\":");
        assertThat(json).contains("\"active\":true");
        assertThat(json).contains("\"identifier\":[{\"system\":\"urn:medikiosk:username\",\"value\":\"patient7\"}]");
        assertThat(json).contains("\"name\":[{\"text\":\"Priya Sharma\"}]");
        assertThat(json).doesNotContain("birthDate");
        assertThat(json).doesNotContain("gender");
    }

    @Test
    void omitsNullElementsButKeepsPopulatedOnes() {
        Consent consent = new Consent(new User("patient7", "secret", "Priya Sharma", Role.PATIENT),
                ConsentType.CLINICAL_CASE_TAKING, "Kiosk case taking");
        String json = FhirJson.toJson(FhirConsentMapper.toFhir(
                new User("patient7", "secret", "Priya Sharma", Role.PATIENT), consent));

        assertThat(json).contains("\"resourceType\":\"Consent\"");
        assertThat(json).contains("\"status\":\"active\"");
        assertThat(json).contains("\"provision\":{\"type\":\"permit\",");
        assertThat(json).contains("\"purpose\":[{\"text\":\"Kiosk case taking\"}]");
        assertThat(json).doesNotContain("\"revokedAt\"");
    }

    @Test
    void bundleSerializesAsFhirCollection() {
        FhirBundle bundle = FhirBundle.collection("bundle-1", "2026-09-11T05:25:00Z", java.util.List.of());

        String json = FhirJson.toJson(bundle);

        assertThat(json).contains("\"resourceType\":\"Bundle\"");
        assertThat(json).contains("\"type\":\"collection\"");
        assertThat(json).contains("\"id\":\"bundle-1\"");
        assertThat(json).contains("\"timestamp\":\"2026-09-11T05:25:00Z\"");
        assertThat(json).doesNotContain("\"entry\"");
    }
}