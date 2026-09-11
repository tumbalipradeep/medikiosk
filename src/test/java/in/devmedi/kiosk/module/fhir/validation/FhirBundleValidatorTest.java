package in.devmedi.kiosk.module.fhir.validation;

import in.devmedi.kiosk.module.fhir.id.FhirIds;
import in.devmedi.kiosk.module.fhir.model.FhirAnnotation;
import in.devmedi.kiosk.module.fhir.model.FhirAttachment;
import in.devmedi.kiosk.module.fhir.model.FhirBundle;
import in.devmedi.kiosk.module.fhir.model.FhirCodeableConcept;
import in.devmedi.kiosk.module.fhir.model.FhirCoding;
import in.devmedi.kiosk.module.fhir.model.FhirConsent;
import in.devmedi.kiosk.module.fhir.model.FhirContent;
import in.devmedi.kiosk.module.fhir.model.FhirContext;
import in.devmedi.kiosk.module.fhir.model.FhirDocumentReference;
import in.devmedi.kiosk.module.fhir.model.FhirEncounter;
import in.devmedi.kiosk.module.fhir.model.FhirEntry;
import in.devmedi.kiosk.module.fhir.model.FhirHumanName;
import in.devmedi.kiosk.module.fhir.model.FhirIdentifier;
import in.devmedi.kiosk.module.fhir.model.FhirObservation;
import in.devmedi.kiosk.module.fhir.model.FhirPatient;
import in.devmedi.kiosk.module.fhir.model.FhirPeriod;
import in.devmedi.kiosk.module.fhir.model.FhirProvision;
import in.devmedi.kiosk.module.fhir.model.FhirQuantity;
import in.devmedi.kiosk.module.fhir.model.FhirReference;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FhirBundleValidatorTest {

    private static final FhirCodeableConcept AMB = new FhirCodeableConcept(List.of(
            FhirCoding.of("http://terminology.hl7.org/CodeSystem/v3-ActCode", "AMB", "ambulatory")), null);
    private static final FhirCodeableConcept QUESTION_CODE = new FhirCodeableConcept(List.of(
            FhirCoding.of("urn:medikiosk:question-code", "q1", null)), null);
    private static final FhirCodeableConcept VITAL_CODE = new FhirCodeableConcept(List.of(
            FhirCoding.of("urn:medikiosk:vital-type", "BLOOD_PRESSURE", null)), null);
    private static final FhirCodeableConcept LAB_CODE = new FhirCodeableConcept(List.of(
            FhirCoding.of("urn:medikiosk:lab-test", "hb", null)), null);
    private static final FhirCodeableConcept PATIENT_PRIVACY = new FhirCodeableConcept(List.of(
            FhirCoding.of("http://terminology.hl7.org/CodeSystem/consentscope",
                    "patient-privacy", null)), null);
    private static final FhirCodeableConcept CONSENT_CATEGORY = new FhirCodeableConcept(List.of(
            FhirCoding.of("urn:medikiosk:consent-type", "DOCUMENT_PROCESSING", null)), null);

    private final FhirBundleValidator validator = new FhirBundleValidator();

    private FhirPatient patient() {
        return FhirPatient.of(FhirIds.patient("u1"), true,
                List.of(FhirIdentifier.of("urn:medikiosk:username", "u1")),
                List.of(FhirHumanName.of("Uma Patel")));
    }

    private FhirEncounter encounter() {
        return FhirEncounter.finished(FhirIds.encounter("c1"), AMB,
                FhirReference.of("Patient/" + FhirIds.patient("u1"), "Uma Patel"), null);
    }

    private FhirObservation answerObservation(String id, FhirReference subject) {
        return FhirObservation.of(id, QUESTION_CODE, subject, null, null, "three days",
                null, null, List.of(FhirAnnotation.of("verbatim=three days; answerSource=AI_GENERATED; "
                        + "questionSource=AI_GENERATED; language=en-IN")));
    }

    private FhirBundle bundle(List<FhirEntry> entries) {
        return FhirBundle.collection(FhirIds.logicalId("Bundle", "c1"),
                "2026-09-11T05:25:00Z", entries);
    }

    private FhirEntry entry(String id, Object resource) {
        return FhirEntry.of(FhirIds.fullUrl(id), resource);
    }

    @Test
    void wellFormedBundleHasNoIssues() {
        FhirBundle bundle = bundle(List.of(
                entry(FhirIds.patient("u1"), patient()),
                entry(FhirIds.encounter("c1"), encounter())));

        assertThat(validator.validate(bundle)).isEmpty();
    }

    @Test
    void fullyPopulatedBundlePassesAllEncodingRules() {
        FhirObservation vital = FhirObservation.of(FhirIds.vital("doc1", "BLOOD_PRESSURE", 0),
                VITAL_CODE, FhirReference.of("Patient/" + FhirIds.patient("u1"), null),
                FhirReference.of("Encounter/" + FhirIds.encounter("c1"), null), null,
                "128/82", null, null, List.of(FhirAnnotation.of("BP: 128/82 mmHg")));
        FhirObservation lab = FhirObservation.of(FhirIds.lab("doc1", 0),
                LAB_CODE, FhirReference.of("Patient/" + FhirIds.patient("u1"), null),
                FhirReference.of("Encounter/" + FhirIds.encounter("c1"), null), null,
                null, FhirQuantity.of(new BigDecimal("14.2"), "g/dL"),
                List.of(new FhirCodeableConcept(List.of(FhirCoding.of(
                        "http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation",
                        "N", null)), null)),
                List.of(FhirAnnotation.of("Hb: 14.2 g/dL")));
        FhirDocumentReference reference = FhirDocumentReference.of(
                FhirIds.document("doc1"), null, "2026-09-11T05:25:00Z",
                List.of(FhirContent.of(FhirAttachment.of("application/pdf", 2048, "lab-report.pdf"))),
                FhirContext.of(FhirReference.of("Encounter/" + FhirIds.encounter("c1"), null)));
        FhirConsent consent = FhirConsent.of(FhirIds.consent("u1", "DOCUMENT_PROCESSING"),
                "active", PATIENT_PRIVACY, List.of(CONSENT_CATEGORY),
                FhirReference.of("Patient/" + FhirIds.patient("u1"), null),
                "2026-09-11T05:25:00Z",
                FhirProvision.of("permit", FhirPeriod.of("2026-01-01T00:00:00Z", null),
                        List.of(FhirCodeableConcept.text("Process my reports"))));

        FhirBundle bundle = bundle(List.of(
                entry(FhirIds.patient("u1"), patient()),
                entry(FhirIds.encounter("c1"), encounter()),
                entry(FhirIds.vital("doc1", "BLOOD_PRESSURE", 0), vital),
                entry(FhirIds.lab("doc1", 0), lab),
                entry(FhirIds.document("doc1"), reference),
                entry(FhirIds.consent("u1", "DOCUMENT_PROCESSING"), consent)));

        assertThat(validator.validate(bundle)).isEmpty();
    }

    @Test
    void duplicateResourceIdIsReported() {
        FhirBundle bundle = bundle(List.of(
                entry(FhirIds.patient("u1"), patient()),
                entry(FhirIds.patient("u1"), patient())));

        assertThat(validator.validate(bundle)).anyMatch(issue -> issue.contains("duplicate resource id"));
    }

    @Test
    void unresolvedReferenceIsReported() {
        FhirBundle bundle = bundle(List.of(
                entry(FhirIds.patient("u1"), patient()),
                entry(FhirIds.encounter("c1"), encounter()),
                entry(FhirIds.answer("c1", 0),
                        answerObservation(FhirIds.answer("c1", 0),
                                FhirReference.of("Patient/does-not-exist", null)))));

        assertThat(validator.validate(bundle))
                .anyMatch(issue -> issue.contains("unresolved reference 'Patient/does-not-exist'"));
    }

    @Test
    void referenceTypeMismatchIsReported() {
        FhirBundle bundle = bundle(List.of(
                entry(FhirIds.patient("u1"), patient()),
                entry(FhirIds.encounter("c1"), encounter()),
                entry(FhirIds.answer("c1", 0),
                        answerObservation(FhirIds.answer("c1", 0),
                                FhirReference.of("Patient/" + FhirIds.encounter("c1"), null)))));

        assertThat(validator.validate(bundle))
                .anyMatch(issue -> issue.contains("reference type mismatch"));
    }

    @Test
    void malformedReferenceIsReported() {
        FhirBundle bundle = bundle(List.of(
                entry(FhirIds.patient("u1"), patient()),
                entry(FhirIds.encounter("c1"),
                        FhirEncounter.finished(FhirIds.encounter("c1"), AMB,
                                FhirReference.of("Patient", "Uma Patel"), null))));

        assertThat(validator.validate(bundle)).anyMatch(issue -> issue.contains("malformed reference"));
    }

    @Test
    void referenceToUnsupportedResourceTypeIsReported() {
        FhirBundle bundle = bundle(List.of(
                entry(FhirIds.patient("u1"), patient()),
                entry(FhirIds.encounter("c1"),
                        FhirEncounter.finished(FhirIds.encounter("c1"), AMB,
                                FhirReference.of("Practitioner/someone", null), null))));

        assertThat(validator.validate(bundle))
                .anyMatch(issue -> issue.contains("unsupported resource type"));
    }

    @Test
    void fullUrlNotMatchingResourceIdIsReported() {
        FhirBundle bundle = bundle(List.of(
                FhirEntry.of("urn:uuid:" + FhirIds.patient("u1"), patient()),
                FhirEntry.of("urn:uuid:some-other-id", encounter())));

        assertThat(validator.validate(bundle))
                .anyMatch(issue -> issue.contains("fullUrl does not match resource id"));
    }

    @Test
    void nonUrnUuidFullUrlIsReported() {
        FhirBundle bundle = bundle(List.of(
                FhirEntry.of("http://example.org/Patient/1", patient())));

        assertThat(validator.validate(bundle))
                .anyMatch(issue -> issue.contains("fullUrl is not a urn:uuid"));
    }

@Test
    void invalidResourceIdIsReported() {
        FhirPatient invalidId = new FhirPatient("Patient", "not a valid id", true,
                List.of(FhirIdentifier.of("urn:medikiosk:username", "u1")),
                List.of(FhirHumanName.of("Uma Patel")));
        FhirBundle bundle = bundle(List.of(
                entry(FhirIds.patient("u1"), invalidId)));

        assertThat(validator.validate(bundle))
                .anyMatch(issue -> issue.contains("Patient id is not a FHIR id"));
    }

    @Test
    void observationWithoutCodeIsReported() {
        FhirReference subject = FhirReference.of("Patient/" + FhirIds.patient("u1"), "Uma Patel");
        FhirBundle bundle = bundle(List.of(
                entry(FhirIds.patient("u1"), patient()),
                entry(FhirIds.encounter("c1"), encounter()),
                entry(FhirIds.answer("c1", 0),
                        FhirObservation.of(FhirIds.answer("c1", 0), null, subject, null,
                                null, "three days", null, null,
                                List.of(FhirAnnotation.of("answerSource=AI_GENERATED; language=en-IN"))))));

        assertThat(validator.validate(bundle)).anyMatch(issue -> issue.contains("Observation has no code"));
    }

    @Test
    void observationWithNeitherValueElementIsStillValidAnswer() {
        FhirReference subject = FhirReference.of("Patient/" + FhirIds.patient("u1"), "Uma Patel");
        FhirBundle bundle = bundle(List.of(
                entry(FhirIds.patient("u1"), patient()),
                entry(FhirIds.encounter("c1"), encounter()),
                entry(FhirIds.answer("c1", 0),
                        FhirObservation.of(FhirIds.answer("c1", 0), QUESTION_CODE, subject, null,
                                null, null, null, null,
                                List.of(FhirAnnotation.of("answerSource=AI_GENERATED; language=en-IN"))))));

        assertThat(validator.validate(bundle)).isEmpty();
    }

    @Test
    void observationWithBothValueElementsIsReported() {
        FhirReference subject = FhirReference.of("Patient/" + FhirIds.patient("u1"), "Uma Patel");
        FhirBundle bundle = bundle(List.of(
                entry(FhirIds.patient("u1"), patient()),
                entry(FhirIds.encounter("c1"), encounter()),
                entry(FhirIds.answer("c1", 0),
                        FhirObservation.of(FhirIds.answer("c1", 0), QUESTION_CODE, subject, null,
                                null, "128/82", FhirQuantity.of(new BigDecimal("128"), "mmHg"), null,
                                List.of(FhirAnnotation.of("answerSource=AI_GENERATED; language=en-IN"))))));

        assertThat(validator.validate(bundle))
                .anyMatch(issue -> issue.contains("both valueString and valueQuantity"));
    }

    @Test
    void observationWithNullQuantityValueIsReported() {
        FhirReference subject = FhirReference.of("Patient/" + FhirIds.patient("u1"), "Uma Patel");
        FhirBundle bundle = bundle(List.of(
                entry(FhirIds.patient("u1"), patient()),
                entry(FhirIds.encounter("c1"), encounter()),
                entry(FhirIds.lab("doc1", 0),
                        FhirObservation.of(FhirIds.lab("doc1", 0), LAB_CODE, subject,
                                FhirReference.of("Encounter/" + FhirIds.encounter("c1"), null),
                                null, null, FhirQuantity.of(null, "g/dL"), null,
                                List.of(FhirAnnotation.of("Hb: 14.2 g/dL"))))));

        assertThat(validator.validate(bundle)).anyMatch(issue -> issue.contains("invalid quantity"));
    }

    @Test
    void invalidInterpretationCodeIsReported() {
        FhirReference subject = FhirReference.of("Patient/" + FhirIds.patient("u1"), "Uma Patel");
        FhirBundle bundle = bundle(List.of(
                entry(FhirIds.patient("u1"), patient()),
                entry(FhirIds.encounter("c1"), encounter()),
                entry(FhirIds.lab("doc1", 0),
                        FhirObservation.of(FhirIds.lab("doc1", 0), LAB_CODE, subject,
                                FhirReference.of("Encounter/" + FhirIds.encounter("c1"), null),
                                null, null, FhirQuantity.of(new BigDecimal("14.2"), "g/dL"),
                                List.of(new FhirCodeableConcept(List.of(FhirCoding.of(
                                        "http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation",
                                        "OVER_THRESHOLD", null)), null)),
                                List.of(FhirAnnotation.of("Hb: 14.2 g/dL"))))));

        assertThat(validator.validate(bundle))
                .anyMatch(issue -> issue.contains("interpretation must be L/H/N"));
    }

    @Test
    void interpretationOnNonLabObservationIsReported() {
        FhirReference subject = FhirReference.of("Patient/" + FhirIds.patient("u1"), "Uma Patel");
        FhirBundle bundle = bundle(List.of(
                entry(FhirIds.patient("u1"), patient()),
                entry(FhirIds.encounter("c1"), encounter()),
                entry(FhirIds.vital("doc1", "BLOOD_PRESSURE", 0),
                        FhirObservation.of(FhirIds.vital("doc1", "BLOOD_PRESSURE", 0), VITAL_CODE,
                                subject, FhirReference.of("Encounter/" + FhirIds.encounter("c1"), null),
                                null, "128/82", null,
                                List.of(new FhirCodeableConcept(List.of(FhirCoding.of(
                                        "http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation",
                                        "L", null)), null)),
                                List.of(FhirAnnotation.of("BP: 128/82 mmHg"))))));

        assertThat(validator.validate(bundle))
                .anyMatch(issue -> issue.contains("only allowed on lab findings"));
    }

    @Test
    void answerObservationLackingProvenanceNoteIsReported() {
        FhirReference subject = FhirReference.of("Patient/" + FhirIds.patient("u1"), "Uma Patel");
        FhirBundle bundle = bundle(List.of(
                entry(FhirIds.patient("u1"), patient()),
                entry(FhirIds.encounter("c1"), encounter()),
                entry(FhirIds.answer("c1", 0),
                        FhirObservation.of(FhirIds.answer("c1", 0), QUESTION_CODE, subject, null,
                                null, "three days", null, null, List.of()))));

        assertThat(validator.validate(bundle))
                .anyMatch(issue -> issue.contains("lacks answerSource/language provenance"));
    }

    @Test
    void findingObservationLackingNoteIsReported() {
        FhirReference subject = FhirReference.of("Patient/" + FhirIds.patient("u1"), "Uma Patel");
        FhirBundle bundle = bundle(List.of(
                entry(FhirIds.patient("u1"), patient()),
                entry(FhirIds.encounter("c1"), encounter()),
                entry(FhirIds.vital("doc1", "BLOOD_PRESSURE", 0),
                        FhirObservation.of(FhirIds.vital("doc1", "BLOOD_PRESSURE", 0), VITAL_CODE,
                                subject, FhirReference.of("Encounter/" + FhirIds.encounter("c1"), null),
                                null, "128/82", null, null, List.of()))));

        assertThat(validator.validate(bundle))
                .anyMatch(issue -> issue.contains("lacks a source-snippet note"));
    }

    @Test
    void codingWithInvalidCodeIsReported() {
        FhirReference subject = FhirReference.of("Patient/" + FhirIds.patient("u1"), "Uma Patel");
        FhirBundle bundle = bundle(List.of(
                entry(FhirIds.patient("u1"), patient()),
                entry(FhirIds.encounter("c1"), encounter()),
                entry(FhirIds.answer("c1", 0),
                        FhirObservation.of(FhirIds.answer("c1", 0),
                                new FhirCodeableConcept(List.of(FhirCoding.of(
                                        "urn:medikiosk:question-code", "  bad code  ", null)), null),
                                subject, null, null, "three days", null, null,
                                List.of(FhirAnnotation.of("answerSource=AI_GENERATED; language=en-IN"))))));

        assertThat(validator.validate(bundle)).anyMatch(issue -> issue.contains("invalid code"));
    }

    @Test
    void codingWithRelativeSystemIsReported() {
        FhirReference subject = FhirReference.of("Patient/" + FhirIds.patient("u1"), "Uma Patel");
        FhirBundle bundle = bundle(List.of(
                entry(FhirIds.patient("u1"), patient()),
                entry(FhirIds.encounter("c1"), encounter()),
                entry(FhirIds.answer("c1", 0),
                        FhirObservation.of(FhirIds.answer("c1", 0),
                                new FhirCodeableConcept(List.of(FhirCoding.of(
                                        "question-code", "q1", null)), null),
                                subject, null, null, "three days", null, null,
                                List.of(FhirAnnotation.of("answerSource=AI_GENERATED; language=en-IN"))))));

        assertThat(validator.validate(bundle))
                .anyMatch(issue -> issue.contains("system is not an absolute URI"));
    }

    @Test
    void documentReferenceWithInvalidContentTypeIsReported() {
        FhirDocumentReference reference = FhirDocumentReference.of(
                FhirIds.document("doc1"), null, "2026-09-11T05:25:00Z",
                List.of(FhirContent.of(FhirAttachment.of("plaintext", 10, "report.txt"))),
                FhirContext.of(FhirReference.of("Encounter/" + FhirIds.encounter("c1"), null)));
        FhirBundle bundle = bundle(List.of(
                entry(FhirIds.patient("u1"), patient()),
                entry(FhirIds.encounter("c1"), encounter()),
                entry(FhirIds.document("doc1"), reference)));

        assertThat(validator.validate(bundle))
                .anyMatch(issue -> issue.contains("contentType is not a MIME type"));
    }

    @Test
    void documentReferenceWithoutContextEncounterIsReported() {
        FhirDocumentReference reference = FhirDocumentReference.of(
                FhirIds.document("doc1"), null, "2026-09-11T05:25:00Z",
                List.of(FhirContent.of(FhirAttachment.of("application/pdf", 10, "report.pdf"))),
                new FhirContext(List.of()));
        FhirBundle bundle = bundle(List.of(
                entry(FhirIds.patient("u1"), patient()),
                entry(FhirIds.encounter("c1"), encounter()),
                entry(FhirIds.document("doc1"), reference)));

        assertThat(validator.validate(bundle)).anyMatch(issue -> issue.contains("context has no encounter"));
    }

    @Test
    void consentWithInvalidStatusIsReported() {
        FhirConsent consent = FhirConsent.of(FhirIds.consent("u1", "DOCUMENT_PROCESSING"),
                "draft", PATIENT_PRIVACY, List.of(CONSENT_CATEGORY),
                FhirReference.of("Patient/" + FhirIds.patient("u1"), null),
                "2026-09-11T05:25:00Z",
                FhirProvision.of("permit", null, List.of(FhirCodeableConcept.text("Process my reports"))));
        FhirBundle bundle = bundle(List.of(
                entry(FhirIds.patient("u1"), patient()),
                entry(FhirIds.encounter("c1"), encounter()),
                entry(FhirIds.consent("u1", "DOCUMENT_PROCESSING"), consent)));

        assertThat(validator.validate(bundle))
                .anyMatch(issue -> issue.contains("Consent status must be active or inactive"));
    }

    @Test
    void consentWithWrongScopeIsReported() {
        FhirConsent consent = FhirConsent.of(FhirIds.consent("u1", "DOCUMENT_PROCESSING"),
                "active",
                new FhirCodeableConcept(List.of(FhirCoding.of(
                        "http://terminology.hl7.org/CodeSystem/consentscope", "treatment", null)), null),
                List.of(CONSENT_CATEGORY),
                FhirReference.of("Patient/" + FhirIds.patient("u1"), null),
                "2026-09-11T05:25:00Z",
                FhirProvision.of("permit", null, List.of(FhirCodeableConcept.text("Process my reports"))));
        FhirBundle bundle = bundle(List.of(
                entry(FhirIds.patient("u1"), patient()),
                entry(FhirIds.encounter("c1"), encounter()),
                entry(FhirIds.consent("u1", "DOCUMENT_PROCESSING"), consent)));

        assertThat(validator.validate(bundle))
                .anyMatch(issue -> issue.contains("scope must be patient-privacy"));
    }

    @Test
    void consentWithUnknownCategorySystemIsReported() {
        FhirConsent consent = FhirConsent.of(FhirIds.consent("u1", "DOCUMENT_PROCESSING"),
                "active", PATIENT_PRIVACY,
                List.of(new FhirCodeableConcept(List.of(FhirCoding.of("http://example.org/other",
                        "anything", null)), null)),
                FhirReference.of("Patient/" + FhirIds.patient("u1"), null),
                "2026-09-11T05:25:00Z",
                FhirProvision.of("permit", null, List.of(FhirCodeableConcept.text("Process my reports"))));
        FhirBundle bundle = bundle(List.of(
                entry(FhirIds.patient("u1"), patient()),
                entry(FhirIds.encounter("c1"), encounter()),
                entry(FhirIds.consent("u1", "DOCUMENT_PROCESSING"), consent)));

        assertThat(validator.validate(bundle))
                .anyMatch(issue -> issue.contains("must use system urn:medikiosk:consent-type"));
    }

    @Test
    void consentWithInvalidProvisionTypeIsReported() {
        FhirConsent consent = FhirConsent.of(FhirIds.consent("u1", "DOCUMENT_PROCESSING"),
                "active", PATIENT_PRIVACY, List.of(CONSENT_CATEGORY),
                FhirReference.of("Patient/" + FhirIds.patient("u1"), null),
                "2026-09-11T05:25:00Z",
                FhirProvision.of("authorize", null, List.of(FhirCodeableConcept.text("Process my reports"))));
        FhirBundle bundle = bundle(List.of(
                entry(FhirIds.patient("u1"), patient()),
                entry(FhirIds.encounter("c1"), encounter()),
                entry(FhirIds.consent("u1", "DOCUMENT_PROCESSING"), consent)));

        assertThat(validator.validate(bundle))
                .anyMatch(issue -> issue.contains("provision type must be permit or deny"));
    }

    @Test
    void patientWithoutIdentifierOrNameIsReported() {
        FhirBundle bundle = bundle(List.of(
                entry(FhirIds.patient("u1"),
                        FhirPatient.of(FhirIds.patient("u1"), true, List.of(), List.of()))));

        assertThat(validator.validate(bundle))
                .anyMatch(issue -> issue.contains("Patient has neither identifier nor name"));
    }

    @Test
    void mismatchingResourceTypeIsReported() {
        FhirBundle bundle = bundle(List.of(
                new FhirEntry("urn:uuid:" + FhirIds.patient("u1"),
                        new FhirPatient("Nonsense", FhirIds.patient("u1"), true,
                                List.of(), List.of()))));

        assertThat(validator.validate(bundle))
                .anyMatch(issue -> issue.contains("resourceType mismatch"));
    }

    @Test
    void nonInstantTimestampIsReported() {
        FhirBundle bundle = FhirBundle.collection(FhirIds.logicalId("Bundle", "c1"),
                "not-an-instant", List.of());

        assertThat(validator.validate(bundle))
                .anyMatch(issue -> issue.contains("timestamp is not a FHIR instant"));
    }

    @Test
    void emptyBundleWithoutEntriesIsValidCollection() {
        FhirBundle bundle = FhirBundle.collection(FhirIds.logicalId("Bundle", "c1"),
                "2026-09-11T05:25:00Z", List.of());

        assertThat(validator.validate(bundle)).isEmpty();
    }
}