package in.devmedi.kiosk.module.fhir.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Minimal FHIR R4 {@code CodeableConcept}.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FhirCodeableConcept(List<FhirCoding> coding,
                                  String text) {

    public static FhirCodeableConcept of(List<FhirCoding> coding, String text) {
        return new FhirCodeableConcept(List.copyOf(coding), text);
    }

    public static FhirCodeableConcept text(String text) {
        return new FhirCodeableConcept(List.of(), text);
    }
}