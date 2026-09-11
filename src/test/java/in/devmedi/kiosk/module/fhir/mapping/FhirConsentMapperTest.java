package in.devmedi.kiosk.module.fhir.mapping;

import in.devmedi.kiosk.module.auth.entity.Role;
import in.devmedi.kiosk.module.auth.entity.User;
import in.devmedi.kiosk.module.consent.entity.Consent;
import in.devmedi.kiosk.module.consent.entity.ConsentType;
import in.devmedi.kiosk.module.fhir.id.FhirIds;
import in.devmedi.kiosk.module.fhir.model.FhirConsent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FhirConsentMapperTest {

    @Test
    void grantedConsentMapsToActivePermit() {
        User user = new User("patient7", "secret", "Priya Sharma", Role.PATIENT);
        Consent consent = new Consent(user, ConsentType.CLINICAL_CASE_TAKING, "Kiosk case taking");

        FhirConsent fhirConsent = FhirConsentMapper.toFhir(user, consent);

        assertThat(fhirConsent.resourceType()).isEqualTo("Consent");
        assertThat(fhirConsent.id())
                .isEqualTo(FhirIds.consent("patient7", "CLINICAL_CASE_TAKING"));
        assertThat(fhirConsent.status()).isEqualTo("active");
        assertThat(fhirConsent.scope().coding().getFirst().code()).isEqualTo("patient-privacy");
        assertThat(fhirConsent.category()).hasSize(1);
        assertThat(fhirConsent.category().getFirst().coding().getFirst().system())
                .isEqualTo("urn:medikiosk:consent-type");
        assertThat(fhirConsent.category().getFirst().coding().getFirst().code())
                .isEqualTo("CLINICAL_CASE_TAKING");
        assertThat(fhirConsent.patient().reference())
                .isEqualTo("Patient/" + FhirIds.patient("patient7"));
        assertThat(fhirConsent.provision().type()).isEqualTo("permit");
        assertThat(fhirConsent.provision().purpose().getFirst().text())
                .isEqualTo("Kiosk case taking");
    }

    @Test
    void revokedConsentMapsToInactiveDenyWithRevocationEndTime() {
        User user = new User("patient7", "secret", "Priya Sharma", Role.PATIENT);
        Consent consent = new Consent(user, ConsentType.DATA_SHARING, "Share with ABDM");
        consent.revoke();

        FhirConsent fhirConsent = FhirConsentMapper.toFhir(user, consent);

        assertThat(fhirConsent.status()).isEqualTo("inactive");
        assertThat(fhirConsent.provision().type()).isEqualTo("deny");
        assertThat(fhirConsent.provision().period().start()).isNotNull();
        assertThat(fhirConsent.provision().period().end()).isNotNull();
    }
}