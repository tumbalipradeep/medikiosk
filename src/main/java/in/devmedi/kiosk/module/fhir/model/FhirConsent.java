package in.devmedi.kiosk.module.fhir.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Minimal FHIR R4 {@code Consent}.
 *
 * <p>Projected from the persisted grant/revoke record. The scope is fixed to
 * {@code patient-privacy} (the code best describing how the kiosk uses the
 * patient's data) and the granular nature is carried in {@code category} via
 * the MediKiosk consent type. The patient-authored purpose text is preserved
 * as the {@code provision.purpose} concept text; it is never reinterpreted.</p>
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FhirConsent(String resourceType,
                          String id,
                          String status,
                          FhirCodeableConcept scope,
                          List<FhirCodeableConcept> category,
                          FhirReference patient,
                          String dateTime,
                          FhirProvision provision) {

    public static FhirConsent of(String id, String status,
                                 FhirCodeableConcept scope, List<FhirCodeableConcept> category,
                                 FhirReference patient, String dateTime, FhirProvision provision) {
        return new FhirConsent("Consent", id, status, scope, category, patient, dateTime, provision);
    }
}