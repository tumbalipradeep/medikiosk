package in.devmedi.kiosk.module.fhir.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Minimal FHIR R4 {@code Patient} projected from the authenticated kiosk
 * {@code users} row. Only demographics that are actually persisted are ever
 * emitted; age, gender, DOB and identifiers other than the username are
 * omitted rather than guessed.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FhirPatient(String resourceType,
                          String id,
                          Boolean active,
                          List<FhirIdentifier> identifier,
                          List<FhirHumanName> name) {

    public static FhirPatient of(String id, boolean active,
                                 List<FhirIdentifier> identifier, List<FhirHumanName> name) {
        return new FhirPatient("Patient", id, active, identifier, name);
    }
}