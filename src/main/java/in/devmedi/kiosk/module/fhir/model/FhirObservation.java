package in.devmedi.kiosk.module.fhir.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Minimal FHIR R4 {@code Observation}.
 *
 * <p>Exactly one {@code value} element is populated per observation:
 * {@code valueString} when the persisted value is free text (patient answers,
 * composite vitals such as blood pressure), {@code valueQuantity} when the
 * persisted value is a single parseable number. Interpretations (lab
 * abnormality) are emitted only when the deterministic pipeline made a claim
 * ({@code UNKNOWN} produces no interpretation).</p>
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FhirObservation(String resourceType,
                              String id,
                              String status,
                              FhirCodeableConcept code,
                              FhirReference subject,
                              FhirReference encounter,
                              String effectiveDateTime,
                              String valueString,
                              FhirQuantity valueQuantity,
                              List<FhirCodeableConcept> interpretation,
                              List<FhirAnnotation> note) {

    public static FhirObservation of(String id, FhirCodeableConcept code,
                                     FhirReference subject, FhirReference encounter,
                                     String effectiveDateTime,
                                     String valueString, FhirQuantity valueQuantity,
                                     List<FhirCodeableConcept> interpretation,
                                     List<FhirAnnotation> note) {
        return new FhirObservation("Observation", id, "final", code, subject, encounter,
                effectiveDateTime, valueString, valueQuantity, interpretation, note);
    }
}