package in.devmedi.kiosk.module.fhir.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One bundle entry: its same-bundle {@code fullUrl} and the contained resource.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FhirEntry(String fullUrl,
                        Object resource) {

    public static FhirEntry of(String fullUrl, Object resource) {
        return new FhirEntry(fullUrl, resource);
    }
}