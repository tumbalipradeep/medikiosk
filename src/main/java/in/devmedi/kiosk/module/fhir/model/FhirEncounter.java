package in.devmedi.kiosk.module.fhir.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Minimal FHIR R4 {@code Encounter}. A completed persisted intake case maps to
 * a finished ambulatory encounter whose period starts at case creation.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FhirEncounter(String resourceType,
                            String id,
                            String status,
                            @JsonProperty("class") FhirCodeableConcept clazz,
                            FhirReference subject,
                            FhirPeriod period) {

    public static FhirEncounter finished(String id, FhirCodeableConcept clazz,
                                         FhirReference subject, FhirPeriod period) {
        return new FhirEncounter("Encounter", id, "finished", clazz, subject, period);
    }
}