package in.devmedi.kiosk.module.fhir.interop;

import in.devmedi.kiosk.module.fhir.model.FhirBundle;

/**
 * Application-level export contract around a generated FHIR Bundle. Makes
 * explicit the resource type, FHIR version expectation, media type, export
 * purpose, case identity, and the generated Bundle itself without duplicating
 * or re-encoding the FHIR model.
 *
 * <p>This contract is the hand-off point between the clinical FHIR generator
 * and the interoperability boundary. It is passed to {@link FhirExportTransport}
 * where a future ABDM adapter would translate it into an outgoing request.
 * The M4.5 {@code LocalOnlyExportTransport} deliberately does <b>not</b>
 * transmit the Bundle anywhere - no network call, no filesystem write, no
 * credential lookup.</p>
 *
 * @param caseId      completed-case identifier
 * @param resourceType always {@code Bundle}
 * @param fhirVersion always {@code 4.0.1}
 * @param mediaType   always {@code application/fhir+json}
 * @param purpose     contract purpose (e.g. {@code CLINICAL_EXPORT})
 * @param bundle      the deterministic collection Bundle
 */
public record FhirExportContract(String caseId,
                                 String resourceType,
                                 String fhirVersion,
                                 String mediaType,
                                 String purpose,
                                 FhirBundle bundle) {

    /** FHIR R4 version string. Does not imply profile-level conformance. */
    public static final String FHIR_VERSION = "4.0.1";

    /** Standard response header exposing the generation target FHIR version. */
    public static final String FHIR_VERSION_HEADER = "X-Fhir-Version";

    /** MIME type for the serialized export representation. */
    public static final String MEDIA_TYPE = "application/fhir+json";

    /** Standard purpose label for the current local clinical export. */
    public static final String PURPOSE_CLINICAL_EXPORT = "CLINICAL_EXPORT";

    /** Creates a contract from a persisted case identifier and its generated Bundle. */
    public static FhirExportContract of(String caseId, FhirBundle bundle) {
        return new FhirExportContract(caseId, bundle.resourceType(), FHIR_VERSION, MEDIA_TYPE,
                PURPOSE_CLINICAL_EXPORT, bundle);
    }
}