package in.devmedi.kiosk.module.fhir.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Minimal FHIR R4 {@code DocumentReference.context} element linking a document
 * to its clinical encounter(s).
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FhirContext(List<FhirReference> encounter) {

    public static FhirContext of(FhirReference encounter) {
        return new FhirContext(List.of(encounter));
    }
}