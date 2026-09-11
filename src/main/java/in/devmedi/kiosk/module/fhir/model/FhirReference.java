package in.devmedi.kiosk.module.fhir.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Minimal FHIR R4 {@code Reference}.
 *
 * <p>Within an export bundle references use the same-bundle logical form
 * {@code <ResourceType>/<logicalId>} so every reference is resolvable against
 * an entry's {@code fullUrl} ({@code urn:uuid:<logicalId>}).</p>
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FhirReference(String reference,
                            String display) {

    public static FhirReference of(String reference, String display) {
        return new FhirReference(reference, display);
    }
}