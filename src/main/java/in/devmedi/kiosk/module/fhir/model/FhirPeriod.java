package in.devmedi.kiosk.module.fhir.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Minimal FHIR R4 {@code Period}.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FhirPeriod(String start,
                         String end) {

    public static FhirPeriod of(String start, String end) {
        return new FhirPeriod(start, end);
    }
}