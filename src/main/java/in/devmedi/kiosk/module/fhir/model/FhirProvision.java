package in.devmedi.kiosk.module.fhir.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Minimal FHIR R4 {@code Consent.provision}: one {@code permit}/{@code deny}
 * rule with the grant/revoke period and the patient's purpose text.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FhirProvision(String type,
                            FhirPeriod period,
                            List<FhirCodeableConcept> purpose) {

    public static FhirProvision of(String type, FhirPeriod period, List<FhirCodeableConcept> purpose) {
        return new FhirProvision(type, period, List.copyOf(purpose));
    }
}