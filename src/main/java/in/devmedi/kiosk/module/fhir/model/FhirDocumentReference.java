package in.devmedi.kiosk.module.fhir.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Minimal FHIR R4 {@code DocumentReference} for an uploaded clinical document
 * that belongs to a completed case. Status is always {@code current}: the
 * stored document is the current representation for that upload.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FhirDocumentReference(String resourceType,
                                    String id,
                                    String status,
                                    FhirCodeableConcept type,
                                    String date,
                                    List<FhirContent> content,
                                    FhirContext context) {

    public static FhirDocumentReference of(String id, FhirCodeableConcept type, String date,
                                           List<FhirContent> content, FhirContext context) {
        return new FhirDocumentReference("DocumentReference", id, "current", type, date, content, context);
    }
}