package in.devmedi.kiosk.module.fhir.mapping;

/**
 * Resolved same-bundle subject/encounter references threaded through resource
 * mappers, so mapping functions stay pure and reference ids are computed once.
 */
public record FhirContextRefs(String subjectReference,
                              String encounterReference) {

    public static FhirContextRefs unbound() {
        return new FhirContextRefs(null, null);
    }
}