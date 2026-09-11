package in.devmedi.kiosk.module.fhir.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Minimal FHIR R4 {@code Annotation} used to carry the verbatim source snippet
 * an extracted finding was decoded from (provenance, not interpretation).
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FhirAnnotation(String text) {

    public static FhirAnnotation of(String text) {
        return new FhirAnnotation(text);
    }
}