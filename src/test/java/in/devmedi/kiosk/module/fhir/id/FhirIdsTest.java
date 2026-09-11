package in.devmedi.kiosk.module.fhir.id;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class FhirIdsTest {

    private static final UUID DNS_NAMESPACE = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");

    private static final Pattern UUID_PATTERN =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-5[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$");

    @Test
    void uuid5MatchesRfc4122TestVector() {
        assertThat(FhirIds.uuid5(DNS_NAMESPACE, "www.example.com").toString())
                .isEqualTo("2ed6657d-e927-568b-95e1-2665a8aea6a2");
    }

    @Test
    void logicalIdIsDeterministicForTheSameBusinessKey() {
        assertThat(FhirIds.logicalId("Patient", "patient"))
                .isEqualTo(FhirIds.logicalId("Patient", "patient"));
        assertThat(FhirIds.logicalId("Observation", "answer#case-1#2"))
                .isEqualTo(FhirIds.logicalId("Observation", "answer#case-1#2"));
    }

    @Test
    void logicalIdDiffersAcrossResourceTypesAndKeys() {
        assertThat(FhirIds.logicalId("Patient", "patient"))
                .isNotEqualTo(FhirIds.logicalId("Encounter", "patient"));
        assertThat(FhirIds.logicalId("Observation", "answer#case-1#1"))
                .isNotEqualTo(FhirIds.logicalId("Observation", "answer#case-1#2"));
    }

    @Test
    void logicalIdsAreUuid5AndFhirClean() {
        for (String id : java.util.List.of(
                FhirIds.patient("patient1"),
                FhirIds.encounter("case-x"),
                FhirIds.answer("case-x", 0),
                FhirIds.vital("doc-1", "BLOOD_PRESSURE", 0),
                FhirIds.lab("doc-1", 2),
                FhirIds.document("doc-1"),
                FhirIds.consent("patient1", "CLINICAL_CASE_TAKING"))) {
            assertThat(UUID_PATTERN.matcher(id).matches())
                    .as("id %s is a version-5 uuid", id)
                    .isTrue();
            assertThat(id).hasSize(36);
        }
    }

    @Test
    void fullUrlWrapsTheLogicalIdInAUuidUrn() {
        String id = FhirIds.patient("patient1");
        assertThat(FhirIds.fullUrl(id)).isEqualTo("urn:uuid:" + id);
    }

    @Test
    void convenienceIdsUseTheirDocumentedBusinessKeys() {
        assertThat(FhirIds.patient("p")).isEqualTo(FhirIds.logicalId("Patient", "p"));
        assertThat(FhirIds.encounter("c")).isEqualTo(FhirIds.logicalId("Encounter", "c"));
        assertThat(FhirIds.answer("c", 3)).isEqualTo(FhirIds.logicalId("Observation", "answer#c#3"));
        assertThat(FhirIds.document("d")).isEqualTo(FhirIds.logicalId("DocumentReference", "d"));
    }
}