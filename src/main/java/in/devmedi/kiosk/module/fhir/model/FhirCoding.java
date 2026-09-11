package in.devmedi.kiosk.module.fhir.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Minimal FHIR R4 {@code Coding}.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FhirCoding(String system,
                         String code,
                         String display) {

    public static FhirCoding of(String system, String code, String display) {
        return new FhirCoding(system, code, display);
    }
}