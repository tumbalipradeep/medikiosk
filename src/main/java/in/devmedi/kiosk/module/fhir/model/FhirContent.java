package in.devmedi.kiosk.module.fhir.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Minimal FHIR R4 {@code DocumentReference.content} element.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FhirContent(FhirAttachment attachment) {

    public static FhirContent of(FhirAttachment attachment) {
        return new FhirContent(attachment);
    }
}