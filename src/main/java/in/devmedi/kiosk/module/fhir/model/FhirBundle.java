package in.devmedi.kiosk.module.fhir.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Minimal FHIR R4 {@code Bundle} of type {@code collection}: a deterministic
 * logical grouping of the MediKiosk clinical data for one completed case.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FhirBundle(String resourceType,
                         String id,
                         String type,
                         String timestamp,
                         List<FhirEntry> entry) {

    public static FhirBundle collection(String id, String timestamp, List<FhirEntry> entry) {
        return new FhirBundle("Bundle", id, "collection", timestamp, List.copyOf(entry));
    }
}