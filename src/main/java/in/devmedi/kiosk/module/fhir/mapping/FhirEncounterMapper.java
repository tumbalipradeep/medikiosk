package in.devmedi.kiosk.module.fhir.mapping;

import in.devmedi.kiosk.module.fhir.id.FhirIds;
import in.devmedi.kiosk.module.fhir.model.FhirCodeableConcept;
import in.devmedi.kiosk.module.fhir.model.FhirCoding;
import in.devmedi.kiosk.module.fhir.model.FhirEncounter;
import in.devmedi.kiosk.module.fhir.model.FhirPeriod;
import in.devmedi.kiosk.module.fhir.model.FhirReference;
import in.devmedi.kiosk.module.physician.entity.CompletedCaseEntity;

import java.util.List;

/**
 * Maps a persisted {@link CompletedCaseEntity} to a FHIR R4 {@code Encounter}.
 *
 * <p>A completed case is the clinical encounter of the kiosk interaction:
 * {@code status = finished} (the case is complete), {@code class = AMB}
 * (ambulatory), and the period starts at case creation. The subject is the
 * {@code null}-safe resulting {@code Patient} reference.</p>
 */
public final class FhirEncounterMapper {

    /** v3 ActCode for ambulatory encounter class. */
    public static final String AMBULATORY_SYSTEM = "http://terminology.hl7.org/CodeSystem/v3-ActCode";
    public static final String AMBULATORY_CODE = "AMB";

    private FhirEncounterMapper() {
    }

    public static FhirEncounter toFhir(CompletedCaseEntity completedCase, FhirReference subject) {
        FhirCodeableConcept clazz = new FhirCodeableConcept(
                List.of(FhirCoding.of(AMBULATORY_SYSTEM, AMBULATORY_CODE, "ambulatory")), null);
        String start = FhirDateTimes.instant(completedCase.getCreatedAt());
        FhirPeriod period = start == null ? null : FhirPeriod.of(start, null);
        return FhirEncounter.finished(FhirIds.encounter(completedCase.getCaseId()), clazz, subject, period);
    }
}