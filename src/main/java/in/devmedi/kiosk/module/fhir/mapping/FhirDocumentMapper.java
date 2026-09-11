package in.devmedi.kiosk.module.fhir.mapping;

import in.devmedi.kiosk.module.document.entity.ClinicalDocument;
import in.devmedi.kiosk.module.document.findings.entity.ClinicalDocumentFindingsLab;
import in.devmedi.kiosk.module.document.findings.entity.ClinicalDocumentFindingsVital;
import in.devmedi.kiosk.module.document.findings.model.AbnormalityStatus;
import in.devmedi.kiosk.module.document.findings.model.VitalType;
import in.devmedi.kiosk.module.fhir.id.FhirIds;
import in.devmedi.kiosk.module.fhir.model.FhirAnnotation;
import in.devmedi.kiosk.module.fhir.model.FhirAttachment;
import in.devmedi.kiosk.module.fhir.model.FhirCodeableConcept;
import in.devmedi.kiosk.module.fhir.model.FhirCoding;
import in.devmedi.kiosk.module.fhir.model.FhirContent;
import in.devmedi.kiosk.module.fhir.model.FhirContext;
import in.devmedi.kiosk.module.fhir.model.FhirDocumentReference;
import in.devmedi.kiosk.module.fhir.model.FhirObservation;
import in.devmedi.kiosk.module.fhir.model.FhirQuantity;
import in.devmedi.kiosk.module.fhir.model.FhirReference;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps persisted {@code ClinicalDocument} rows and their decoded findings to
 * FHIR R4 {@code DocumentReference} and {@code Observation} resources.
 *
 * <p>The uploaded binary itself is never inlined — the attachment carries the
 * stored type, size and filename. Vitals and lab results read from the
 * document are projected as {@code final} observations (they are persisted
 * extraction outputs belonging to the case), the canonical registry name under
 * the MediKiosk system codes, the numeric value as a quantity (free-text/composite
 * values stay value strings), and the verbatim source line as an annotation
 * so provenance survives. Reference ranges are not asserted in M4.1.</p>
 */
public final class FhirDocumentMapper {

    public static final String VITAL_SYSTEM = "urn:medikiosk:vital-type";
    public static final String LAB_SYSTEM = "urn:medikiosk:lab-test";
    public static final String REPORT_TYPE_SYSTEM = "urn:medikiosk:report-type";
    public static final String INTERPRETATION_SYSTEM =
            "http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation";

    private FhirDocumentMapper() {
    }

    public static FhirDocumentReference toDocumentReference(ClinicalDocument document,
                                                            String reportType,
                                                            String encounterReference) {
        FhirCodeableConcept type = (reportType == null || reportType.isBlank())
                ? null
                : new FhirCodeableConcept(
                        List.of(FhirCoding.of(REPORT_TYPE_SYSTEM, reportType, reportType)), null);
        FhirAttachment attachment = FhirAttachment.of(document.getContentType(),
                document.getFileSize(), document.getOriginalFilename());
        FhirContext context = encounterReference == null
                ? null : FhirContext.of(FhirReference.of(encounterReference, null));
        return FhirDocumentReference.of(
                FhirIds.document(document.getDocumentId()),
                type,
                FhirDateTimes.instant(document.getUploadedAt()),
                List.of(FhirContent.of(attachment)),
                context);
    }

    public static FhirObservation toVitalObservation(ClinicalDocument document,
                                                     ClinicalDocumentFindingsVital vital,
                                                     FhirContextRefs refs) {
        FhirCodeableConcept code = new FhirCodeableConcept(List.of(
                FhirCoding.of(VITAL_SYSTEM, vital.getType().name(), vitalDisplay(vital.getType()))), null);
        return FhirObservation.of(
                FhirIds.vital(document.getDocumentId(), vital.getType().name(), vital.getOccurrenceIndex()),
                code,
                subjectRef(refs),
                encounterRef(refs),
                null,
                quantityString(vital.getValue()),
                quantity(vital.getValue(), vital.getUnit()),
                null,
                List.of(FhirAnnotation.of(vital.getSourceSnippet())));
    }

    public static FhirObservation toLabObservation(ClinicalDocument document,
                                                   ClinicalDocumentFindingsLab lab,
                                                   FhirContextRefs refs) {
        FhirCodeableConcept code = new FhirCodeableConcept(
                List.of(FhirCoding.of(LAB_SYSTEM, lab.getTestName(), lab.getTestName())), null);
        return FhirObservation.of(
                FhirIds.lab(document.getDocumentId(), lab.getOccurrenceIndex()),
                code,
                subjectRef(refs),
                encounterRef(refs),
                null,
                quantityString(lab.getValue()),
                quantity(lab.getValue(), lab.getUnit()),
                interpretation(lab.getAbnormalityStatus()),
                List.of(FhirAnnotation.of(lab.getSourceSnippet())));
    }

    /** Returns the Observation interpretation coding only when the pipeline made a claim. */
    static List<FhirCodeableConcept> interpretation(String abnormalityStatus) {
        AbnormalityStatus status = parseStatus(abnormalityStatus);
        if (status == null || status == AbnormalityStatus.UNKNOWN) {
            return null;
        }
        FhirCoding coding = switch (status) {
            case LOW -> FhirCoding.of(INTERPRETATION_SYSTEM, "L", "Low");
            case HIGH -> FhirCoding.of(INTERPRETATION_SYSTEM, "H", "High");
            case NORMAL -> FhirCoding.of(INTERPRETATION_SYSTEM, "N", "Normal");
            case UNKNOWN -> null;
        };
        return List.of(new FhirCodeableConcept(List.of(coding), null));
    }

    private static AbnormalityStatus parseStatus(String value) {
        if (value == null) {
            return null;
        }
        try {
            return AbnormalityStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** A numeric quantity when the persisted value is a single number, otherwise {@code null}. */
    static FhirQuantity quantity(String value, String unit) {
        BigDecimal decimal = parseDecimal(value);
        return decimal == null ? null : FhirQuantity.of(decimal, unit);
    }

    /** The verbatim value string when it is not a clean single number (e.g. {@code 128/82}). */
    static String quantityString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return parseDecimal(value) == null ? value : null;
    }

    private static BigDecimal parseDecimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static FhirReference subjectRef(FhirContextRefs refs) {
        return refs.subjectReference() == null ? null : FhirReference.of(refs.subjectReference(), null);
    }

    private static FhirReference encounterRef(FhirContextRefs refs) {
        return refs.encounterReference() == null ? null : FhirReference.of(refs.encounterReference(), null);
    }

    private static String vitalDisplay(VitalType type) {
        return switch (type) {
            case BLOOD_PRESSURE -> "Blood pressure";
            case HEART_RATE -> "Heart rate";
            case RESPIRATORY_RATE -> "Respiratory rate";
            case TEMPERATURE -> "Temperature";
            case OXYGEN_SATURATION -> "Oxygen saturation";
            case HEIGHT -> "Height";
            case WEIGHT -> "Weight";
            case BMI -> "Body mass index";
        };
    }
}