package in.devmedi.kiosk.module.fhir.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Minimal FHIR R4 {@code Identifier}.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FhirIdentifier(String system,
                             String value) {

    public static FhirIdentifier of(String system, String value) {
        return new FhirIdentifier(system, value);
    }
}