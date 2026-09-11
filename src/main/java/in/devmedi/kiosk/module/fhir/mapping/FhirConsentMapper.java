package in.devmedi.kiosk.module.fhir.mapping;

import in.devmedi.kiosk.module.auth.entity.User;
import in.devmedi.kiosk.module.consent.entity.Consent;
import in.devmedi.kiosk.module.consent.entity.ConsentState;
import in.devmedi.kiosk.module.consent.entity.ConsentType;
import in.devmedi.kiosk.module.fhir.id.FhirIds;
import in.devmedi.kiosk.module.fhir.model.FhirCodeableConcept;
import in.devmedi.kiosk.module.fhir.model.FhirCoding;
import in.devmedi.kiosk.module.fhir.model.FhirConsent;
import in.devmedi.kiosk.module.fhir.model.FhirPeriod;
import in.devmedi.kiosk.module.fhir.model.FhirProvision;
import in.devmedi.kiosk.module.fhir.model.FhirReference;

import java.util.List;

/**
 * Maps a persisted {@link Consent} record to a FHIR R4 {@code Consent}.
 *
 * <p>The scope is fixed to {@code patient-privacy} (the closest generic scope
 * for how the kiosk uses patient data); the granular type is carried as the
 * category coding. {@code GRANTED} becomes an {@code active} provision with a
 * {@code permit} rule, {@code REVOKED} an {@code inactive} one with a
 * {@code deny} rule. The patient-authored purpose text is preserved verbatim
 * as the provision purpose concept text.</p>
 */
public final class FhirConsentMapper {

    public static final String SCOPE_SYSTEM = "http://terminology.hl7.org/CodeSystem/consentscope";
    public static final String SCOPE_CODE = "patient-privacy";
    public static final String CONSENT_TYPE_SYSTEM = "urn:medikiosk:consent-type";

    private FhirConsentMapper() {
    }

    public static FhirConsent toFhir(User user, Consent consent) {
        boolean granted = consent.getState() == ConsentState.GRANTED;
        String consentType = consent.getConsentType().name();

        FhirCodeableConcept scope = new FhirCodeableConcept(
                List.of(FhirCoding.of(SCOPE_SYSTEM, SCOPE_CODE, "patient privacy")), null);
        FhirCodeableConcept category = new FhirCodeableConcept(List.of(
                FhirCoding.of(CONSENT_TYPE_SYSTEM, consentType, consent.getConsentType().readableName())), null);

        String grantedAt = FhirDateTimes.instant(consent.getGrantedAt());
        FhirPeriod period = grantedAt == null ? null
                : FhirPeriod.of(grantedAt, FhirDateTimes.instant(consent.getRevokedAt()));
        FhirProvision provision = FhirProvision.of(
                granted ? "permit" : "deny",
                period,
                List.of(FhirCodeableConcept.text(consent.getPurpose())));

        return FhirConsent.of(
                FhirIds.consent(user.getUsername(), consentType),
                granted ? "active" : "inactive",
                scope,
                List.of(category),
                FhirReference.of("Patient/" + FhirIds.patient(user.getUsername()), user.getDisplayName()),
                FhirDateTimes.instant(consent.getGrantedAt()),
                provision);
    }
}