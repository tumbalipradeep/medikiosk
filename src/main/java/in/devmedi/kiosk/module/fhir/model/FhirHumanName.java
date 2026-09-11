package in.devmedi.kiosk.module.fhir.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Minimal FHIR R4 {@code HumanName} holding the displayed name text only.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FhirHumanName(String text) {

    public static FhirHumanName of(String text) {
        return new FhirHumanName(text);
    }
}