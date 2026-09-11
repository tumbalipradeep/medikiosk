package in.devmedi.kiosk.module.fhir.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Minimal FHIR R4 {@code Attachment} describing the stored clinical document
 * binary (type, size, filename). The binary content itself is deliberately
 * never inlined; it lives on the local filesystem, not in a database.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FhirAttachment(String contentType,
                             long size,
                             String title) {

    public static FhirAttachment of(String contentType, long size, String title) {
        return new FhirAttachment(contentType, size, title);
    }
}