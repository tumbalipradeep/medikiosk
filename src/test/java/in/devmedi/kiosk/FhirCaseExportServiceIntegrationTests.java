package in.devmedi.kiosk;

import in.devmedi.kiosk.module.auth.entity.User;
import in.devmedi.kiosk.module.auth.repository.UserRepository;
import in.devmedi.kiosk.module.clinical.dialogue.AnswerSource;
import in.devmedi.kiosk.module.clinical.dialogue.ClinicalAnswer;
import in.devmedi.kiosk.module.clinical.dialogue.ClinicalConversationResult;
import in.devmedi.kiosk.module.clinical.dialogue.QuestionSource;
import in.devmedi.kiosk.module.consent.entity.Consent;
import in.devmedi.kiosk.module.consent.entity.ConsentType;
import in.devmedi.kiosk.module.consent.repository.ConsentRepository;
import in.devmedi.kiosk.module.document.entity.ClinicalDocument;
import in.devmedi.kiosk.module.document.entity.ClinicalDocumentExtraction;
import in.devmedi.kiosk.module.document.extraction.ExtractionErrorCategory;
import in.devmedi.kiosk.module.document.extraction.ExtractionOutcome;
import in.devmedi.kiosk.module.document.extraction.ExtractionResult;
import in.devmedi.kiosk.module.document.extraction.ExtractionStatus;
import in.devmedi.kiosk.module.document.findings.entity.ClinicalDocumentFindings;
import in.devmedi.kiosk.module.document.findings.model.AbnormalityStatus;
import in.devmedi.kiosk.module.document.findings.model.EncounterMetadata;
import in.devmedi.kiosk.module.document.findings.model.InteractionAnalysisStatus;
import in.devmedi.kiosk.module.document.findings.model.LabResult;
import in.devmedi.kiosk.module.document.findings.model.PatientIdentifiers;
import in.devmedi.kiosk.module.document.findings.model.StructuredFindings;
import in.devmedi.kiosk.module.document.findings.model.VitalSign;
import in.devmedi.kiosk.module.document.findings.model.VitalType;
import in.devmedi.kiosk.module.document.findings.repository.ClinicalDocumentFindingsRepository;
import in.devmedi.kiosk.module.document.repository.ClinicalDocumentExtractionRepository;
import in.devmedi.kiosk.module.document.repository.ClinicalDocumentRepository;
import in.devmedi.kiosk.module.fhir.id.FhirIds;
import in.devmedi.kiosk.module.fhir.json.FhirJson;
import in.devmedi.kiosk.module.fhir.model.FhirBundle;
import in.devmedi.kiosk.module.fhir.model.FhirDocumentReference;
import in.devmedi.kiosk.module.fhir.model.FhirEncounter;
import in.devmedi.kiosk.module.fhir.model.FhirEntry;
import in.devmedi.kiosk.module.fhir.model.FhirObservation;
import in.devmedi.kiosk.module.fhir.service.FhirCaseExportService;
import in.devmedi.kiosk.module.fhir.validation.FhirBundleValidator;
import in.devmedi.kiosk.module.physician.entity.CompletedCaseEntity;
import in.devmedi.kiosk.module.physician.repository.CompletedCaseRepository;
import in.devmedi.kiosk.module.physician.service.CompletedCase;
import in.devmedi.kiosk.module.physician.service.CompletedCasePersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class FhirCaseExportServiceIntegrationTests {

    @Autowired
    private FhirCaseExportService fhirCaseExportService;

    @Autowired
    private CompletedCasePersistenceService persistence;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompletedCaseRepository completedCaseRepository;

    @Autowired
    private ClinicalDocumentRepository documentRepository;

    @Autowired
    private ClinicalDocumentExtractionRepository extractionRepository;

    @Autowired
    private ClinicalDocumentFindingsRepository findingsRepository;

    @Autowired
    private ConsentRepository consentRepository;

    @Autowired
    private FhirBundleValidator bundleValidator;

    @BeforeEach
    void clearPersistedData() {
        findingsRepository.findAll().forEach(findingsRepository::delete);
        extractionRepository.deleteAllInBatch();
        documentRepository.deleteAllInBatch();
        consentRepository.deleteAll();
        persistence.deleteAll();
    }

    private ClinicalConversationResult buildAnswers(int answerCount) {
        ClinicalConversationResult result = new ClinicalConversationResult();
        for (int i = 0; i < answerCount; i++) {
            result.record(new ClinicalAnswer("question_" + i, "HISTORY_OF_PRESENT_ILLNESS",
                    "SOCRATES", "Question text " + i, "Answer " + i));
        }
        return result;
    }

    private void saveDocumentWithFindings(CompletedCaseEntity caseEntity, User patient) {
        ClinicalDocument document = ClinicalDocument.create("doc-fhir-1", caseEntity, patient,
                "lab-report.pdf", "stored.pdf", "application/pdf", 2048);
        this.documentRepository.save(document);

        ClinicalDocumentExtraction extraction = ClinicalDocumentExtraction.create(document,
                new ExtractionOutcome(ExtractionStatus.EXTRACTED, ExtractionErrorCategory.NONE, null,
                        "report text", 1, List.of(new ExtractionResult.PageText(1, "report text"))));
        this.extractionRepository.save(extraction);

        StructuredFindings structured = new StructuredFindings(
                new PatientIdentifiers("Ravi Kumar", null, "Male", "MRN123"),
                new EncounterMetadata(null, null, null, null, "Lab Report"),
                List.of(new VitalSign(VitalType.BLOOD_PRESSURE, "128/82", "mmHg",
                        "BP: 128/82 mmHg", 0)),
                List.of(new LabResult("Hemoglobin", "14.2 g/dL", "14.2", "g/dL",
                        "13.0 - 17.0", null, "Hb: 14.2 g/dL", 0, AbnormalityStatus.NORMAL)),
                List.of(), InteractionAnalysisStatus.NOT_AVAILABLE);
        this.findingsRepository.save(ClinicalDocumentFindings.create(extraction, structured));
    }

    private User patient() {
        return userRepository.findByUsername("patient").orElseThrow();
    }

    @Test
    void exportsFullBundleForAPersistedCase() {
        User patient = patient();
        CompletedCase completed = CompletedCase.withNewId(buildAnswers(2), patient.getId());
        persistence.save(completed, patient.getId());

        CompletedCaseEntity caseEntity = completedCaseRepository.findByCaseId(completed.id()).orElseThrow();
        saveDocumentWithFindings(caseEntity, patient);
        consentRepository.save(new Consent(patient, ConsentType.DOCUMENT_PROCESSING, "Process my reports"));

        FhirBundle bundle = fhirCaseExportService.exportCompletedCase(completed.id()).orElseThrow();

        assertThat(bundle.resourceType()).isEqualTo("Bundle");
        assertThat(bundle.type()).isEqualTo("collection");
        assertThat(bundle.id()).isEqualTo(FhirIds.logicalId("Bundle", completed.id()));
        assertThat(bundle.timestamp()).matches("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z$");
        assertThat(bundle.entry()).hasSize(8);

        FhirEntry patientEntry = bundle.entry().getFirst();
        FhirEntry encounterEntry = bundle.entry().get(1);
        assertThat(patientEntry.fullUrl()).isEqualTo("urn:uuid:" + FhirIds.patient(patient.getUsername()));
        assertThat(encounterEntry.fullUrl()).isEqualTo("urn:uuid:" + FhirIds.encounter(completed.id()));

        assertThat(bundle.entry()).extracting(FhirEntry::resource)
                .extracting(FhirCaseExportServiceIntegrationTests::resourceType)
                .containsExactly("Patient", "Encounter", "Observation", "Observation",
                        "DocumentReference", "Observation", "Observation", "Consent");
    }

    @Test
    void answerObservationsEmitVerbatimValuesInAnswerOrder() {
        User patient = patient();
        CompletedCase completed = CompletedCase.withNewId(buildAnswers(2), patient.getId());
        persistence.save(completed, patient.getId());

        FhirBundle bundle = fhirCaseExportService.exportCompletedCase(completed.id()).orElseThrow();

        List<FhirObservation> answerObservations = bundle.entry().stream()
                .map(FhirEntry::resource)
                .filter(FhirObservation.class::isInstance)
                .map(FhirObservation.class::cast)
                .toList();
        assertThat(answerObservations).hasSize(2);
        assertThat(answerObservations).extracting(FhirObservation::valueString)
                .containsExactly("Answer 0", "Answer 1");
        assertThat(answerObservations.getFirst().id())
                .isEqualTo(FhirIds.answer(completed.id(), 0));
    }

    @Test
    void everyInternalReferenceResolvesToABundleFullUrl() {
        User patient = patient();
        CompletedCase completed = CompletedCase.withNewId(buildAnswers(1), patient.getId());
        persistence.save(completed, patient.getId());

        CompletedCaseEntity caseEntity = completedCaseRepository.findByCaseId(completed.id()).orElseThrow();
        saveDocumentWithFindings(caseEntity, patient);

        FhirBundle bundle = fhirCaseExportService.exportCompletedCase(completed.id()).orElseThrow();
        List<String> fullUrls = bundle.entry().stream().map(FhirEntry::fullUrl).toList();

        for (String reference : collectReferences(bundle)) {
            String logicalId = reference.substring(reference.indexOf('/') + 1);
            assertThat(fullUrls.contains("urn:uuid:" + logicalId))
                    .as("reference %s resolves inside the bundle", reference)
                    .isTrue();
        }
    }

    @Test
    void documentFindingsAreProjectedWithTheirDerivedValues() {
        User patient = patient();
        CompletedCase completed = CompletedCase.withNewId(buildAnswers(0), patient.getId());
        persistence.save(completed, patient.getId());

        CompletedCaseEntity caseEntity = completedCaseRepository.findByCaseId(completed.id()).orElseThrow();
        saveDocumentWithFindings(caseEntity, patient);

        FhirBundle bundle = fhirCaseExportService.exportCompletedCase(completed.id()).orElseThrow();
        List<FhirObservation> observations = bundle.entry().stream()
                .map(FhirEntry::resource)
                .filter(FhirObservation.class::isInstance)
                .map(FhirObservation.class::cast)
                .toList();

        FhirObservation vital = observations.getFirst();
        assertThat(vital.id()).isEqualTo(FhirIds.vital("doc-fhir-1", "BLOOD_PRESSURE", 0));
        assertThat(vital.valueString()).isEqualTo("128/82");
        assertThat(vital.valueQuantity()).isNull();
        assertThat(vital.note().getFirst().text()).isEqualTo("BP: 128/82 mmHg");

        FhirObservation lab = observations.get(1);
        assertThat(lab.id()).isEqualTo(FhirIds.lab("doc-fhir-1", 0));
        assertThat(lab.valueString()).isNull();
        assertThat(lab.valueQuantity().value()).isEqualByComparingTo("14.2");
        assertThat(lab.valueQuantity().unit()).isEqualTo("g/dL");
        assertThat(lab.interpretation().getFirst().coding().getFirst().code()).isEqualTo("N");
    }

    @Test
    void consentsAreIncludedWhenThePatientHasOne() {
        User patient = patient();
        CompletedCase completed = CompletedCase.withNewId(buildAnswers(0), patient.getId());
        persistence.save(completed, patient.getId());
        consentRepository.save(new Consent(patient, ConsentType.DATA_SHARING, "Share with ABDM"));

        FhirBundle bundle = fhirCaseExportService.exportCompletedCase(completed.id()).orElseThrow();

        long consentEntries = bundle.entry().stream()
                .map(FhirEntry::resource)
                .filter(in.devmedi.kiosk.module.fhir.model.FhirConsent.class::isInstance)
                .count();
        assertThat(consentEntries).isEqualTo(1);
    }

    @Test
    void caseWithoutOwnerStillExportsAnEncounterOnlyBundle() {
        CompletedCase completed = CompletedCase.withNewId(buildAnswers(1));
        persistence.save(completed);

        FhirBundle bundle = fhirCaseExportService.exportCompletedCase(completed.id()).orElseThrow();

        assertThat(bundle.entry()).hasSize(2);
        assertThat(bundle.entry().getFirst().resource())
                .isInstanceOf(in.devmedi.kiosk.module.fhir.model.FhirEncounter.class);
    }

    @Test
    void nonexistentCaseReturnsEmpty() {
        assertThat(fhirCaseExportService.exportCompletedCase("case-not-there")).isEmpty();
    }

    @Test
    void bundleSerializesToFhirJson() {
        User patient = patient();
        CompletedCase completed = CompletedCase.withNewId(buildAnswers(1), patient.getId());
        persistence.save(completed, patient.getId());

        FhirBundle bundle = fhirCaseExportService.exportCompletedCase(completed.id()).orElseThrow();
        String json = FhirJson.toJson(bundle);

        assertThat(json).contains("\"resourceType\":\"Bundle\"");
        assertThat(json).contains("\"resourceType\":\"Patient\"");
        assertThat(json).contains("\"resourceType\":\"Encounter\"");
        assertThat(json).contains("\"resourceType\":\"Observation\"");
        assertThat(json).doesNotContain("createdAt");
    }

    @Test
    void exportIsByteForByteDeterministic() {
        User patient = patient();
        CompletedCase completed = CompletedCase.withNewId(buildAnswers(2), patient.getId());
        persistence.save(completed, patient.getId());

        CompletedCaseEntity caseEntity = completedCaseRepository.findByCaseId(completed.id()).orElseThrow();
        saveDocumentWithFindings(caseEntity, patient);
        consentRepository.save(new Consent(patient, ConsentType.DATA_SHARING, "Share with ABDM"));

        String first = FhirJson.toJson(fhirCaseExportService.exportCompletedCase(completed.id()).orElseThrow());
        String second = FhirJson.toJson(fhirCaseExportService.exportCompletedCase(completed.id()).orElseThrow());

        assertThat(second).isEqualTo(first);
    }

    @Test
    void bundleContainsNoDuplicateResourceIds() {
        User patient = patient();
        CompletedCase completed = CompletedCase.withNewId(buildAnswers(3), patient.getId());
        persistence.save(completed, patient.getId());

        CompletedCaseEntity caseEntity = completedCaseRepository.findByCaseId(completed.id()).orElseThrow();
        saveDocumentWithFindings(caseEntity, patient);
        consentRepository.save(new Consent(patient, ConsentType.DOCUMENT_PROCESSING, "Process reports"));

        FhirBundle bundle = fhirCaseExportService.exportCompletedCase(completed.id()).orElseThrow();

        List<String> ids = bundle.entry().stream().map(FhirEntry::resource)
                .map(FhirCaseExportServiceIntegrationTests::resourceId)
                .toList();
        assertThat(ids).doesNotHaveDuplicates();
    }

    @Test
    void assembledBundlePassesStructuralValidation() {
        User patient = patient();
        CompletedCase completed = CompletedCase.withNewId(buildAnswers(2), patient.getId());
        persistence.save(completed, patient.getId());

        CompletedCaseEntity caseEntity = completedCaseRepository.findByCaseId(completed.id()).orElseThrow();
        saveDocumentWithFindings(caseEntity, patient);
        consentRepository.save(new Consent(patient, ConsentType.CLINICAL_CASE_TAKING, "Kiosk case taking"));

        FhirBundle bundle = fhirCaseExportService.exportCompletedCase(completed.id()).orElseThrow();

        assertThat(bundleValidator.validate(bundle)).isEmpty();
    }

    @Test
    void documentWithoutExtractionBecomesMetadataOnlyDocumentReference() {
        User patient = patient();
        CompletedCase completed = CompletedCase.withNewId(buildAnswers(0), patient.getId());
        persistence.save(completed, patient.getId());

        CompletedCaseEntity caseEntity = completedCaseRepository.findByCaseId(completed.id()).orElseThrow();
        documentRepository.save(ClinicalDocument.create("doc-no-extract", caseEntity, patient,
                "scan.pdf", "stored.pdf", "image/jpeg", 4096));

        FhirBundle bundle = fhirCaseExportService.exportCompletedCase(completed.id()).orElseThrow();

        FhirDocumentReference reference = (FhirDocumentReference) bundle.entry().stream()
                .map(FhirEntry::resource)
                .filter(FhirDocumentReference.class::isInstance)
                .findFirst().orElseThrow();
        assertThat(reference.id()).isEqualTo(FhirIds.document("doc-no-extract"));
        assertThat(reference.content().getFirst().attachment().contentType()).isEqualTo("image/jpeg");
        assertThat(reference.content().getFirst().attachment().size()).isEqualTo(4096);
        assertThat(reference.type()).isNull();
        assertThat(bundle.entry()).extracting(FhirEntry::resource)
                .filteredOn(FhirObservation.class::isInstance).isEmpty();
        assertThat(bundleValidator.validate(bundle)).isEmpty();
    }

    @Test
    void encounterDatesArePreservedFromPersistedCase() {
        User patient = patient();
        CompletedCase completed = CompletedCase.withNewId(buildAnswers(1), patient.getId());
        persistence.save(completed, patient.getId());

        FhirBundle bundle = fhirCaseExportService.exportCompletedCase(completed.id()).orElseThrow();

        FhirEncounter encounter = (FhirEncounter) bundle.entry().stream()
                .map(FhirEntry::resource)
                .filter(FhirEncounter.class::isInstance)
                .findFirst().orElseThrow();
        String createdAt = in.devmedi.kiosk.module.fhir.mapping.FhirDateTimes
                .instant(completedCaseRepository.findByCaseId(completed.id()).orElseThrow().getCreatedAt());
        assertThat(encounter.period().start()).isEqualTo(createdAt);
    }

    @Test
    void verbatimValuesAndProvenanceSurviveExport() {
        User patient = patient();
        ClinicalConversationResult result = new ClinicalConversationResult();
        result.record(new ClinicalAnswer("hpi_onset", "HISTORY_OF_PRESENT_ILLNESS", "ONSET",
                "When did it first start?", "Could you tell me when the discomfort began?",
                "Three days ago", QuestionSource.AI_GENERATED, AnswerSource.VOICE, "te-IN"));
        CompletedCase completed = CompletedCase.withNewId(result, patient.getId());
        persistence.save(completed, patient.getId());

        FhirBundle bundle = fhirCaseExportService.exportCompletedCase(completed.id()).orElseThrow();

        FhirObservation answer = bundle.entry().stream()
                .map(FhirEntry::resource)
                .filter(FhirObservation.class::isInstance)
                .map(FhirObservation.class::cast)
                .findFirst().orElseThrow();
        assertThat(answer.valueString()).isEqualTo("Three days ago");
        assertThat(answer.code().coding().getFirst().code()).isEqualTo("hpi_onset");
        assertThat(answer.code().text()).isEqualTo("Could you tell me when the discomfort began?");
        assertThat(answer.note().getFirst().text()).contains("answerSource=VOICE",
                "questionSource=AI_GENERATED", "language=te-IN");
    }

    @Test
    void caseWithoutDocumentsExportsPatientEncounterAndAnswers() {
        User patient = patient();
        CompletedCase completed = CompletedCase.withNewId(buildAnswers(2), patient.getId());
        persistence.save(completed, patient.getId());

        FhirBundle bundle = fhirCaseExportService.exportCompletedCase(completed.id()).orElseThrow();

        assertThat(bundle.entry()).hasSize(4);
        assertThat(bundle.entry()).extracting(FhirEntry::resource)
                .extracting(FhirCaseExportServiceIntegrationTests::resourceType)
                .containsExactly("Patient", "Encounter", "Observation", "Observation");
        assertThat(bundleValidator.validate(bundle)).isEmpty();
    }

    @Test
    void caseWithoutOwnerButWithDocumentsStillExports() {
        CompletedCase completed = CompletedCase.withNewId(buildAnswers(0));
        persistence.save(completed);

        CompletedCaseEntity caseEntity = completedCaseRepository.findByCaseId(completed.id()).orElseThrow();
        User patient = patient();
        saveDocumentWithFindings(caseEntity, patient);

        FhirBundle bundle = fhirCaseExportService.exportCompletedCase(completed.id()).orElseThrow();

        assertThat(bundle.entry()).extracting(FhirEntry::resource)
                .extracting(FhirCaseExportServiceIntegrationTests::resourceType)
                .containsExactly("Encounter", "DocumentReference", "Observation", "Observation");
        assertThat(bundleValidator.validate(bundle)).isEmpty();
    }

    @Test
    void findingsWithoutVitalsOrLabsProduceMetadataOnlyDocumentReference() {
        User patient = patient();
        CompletedCase completed = CompletedCase.withNewId(buildAnswers(0), patient.getId());
        persistence.save(completed, patient.getId());
        CompletedCaseEntity caseEntity = completedCaseRepository.findByCaseId(completed.id()).orElseThrow();

        ClinicalDocument document = ClinicalDocument.create("doc-empty-findings", caseEntity, patient,
                "scan.pdf", "stored.pdf", "application/pdf", 512);
        documentRepository.save(document);
        ClinicalDocumentExtraction extraction = ClinicalDocumentExtraction.create(document,
                new ExtractionOutcome(ExtractionStatus.EXTRACTED, ExtractionErrorCategory.NONE, null,
                        "report text", 1, List.of(new ExtractionResult.PageText(1, "report text"))));
        extractionRepository.save(extraction);
        StructuredFindings structured = new StructuredFindings(
                new PatientIdentifiers("Ravi Kumar", null, "Male", "MRN123"),
                new EncounterMetadata(null, null, null, null, "Lab Report"),
                List.of(), List.of(), List.of(), InteractionAnalysisStatus.NOT_AVAILABLE);
        findingsRepository.save(ClinicalDocumentFindings.create(extraction, structured));

        FhirBundle bundle = fhirCaseExportService.exportCompletedCase(completed.id()).orElseThrow();

        FhirDocumentReference reference = (FhirDocumentReference) bundle.entry().stream()
                .map(FhirEntry::resource)
                .filter(FhirDocumentReference.class::isInstance)
                .findFirst().orElseThrow();
        assertThat(reference.id()).isEqualTo(FhirIds.document("doc-empty-findings"));
        assertThat(bundle.entry()).extracting(FhirEntry::resource)
                .filteredOn(FhirObservation.class::isInstance).isEmpty();
        assertThat(bundleValidator.validate(bundle)).isEmpty();
    }

    @Test
    void nonNumericVitalValueIsCarriedAsVerbatimString() {
        User patient = patient();
        CompletedCase completed = CompletedCase.withNewId(buildAnswers(0), patient.getId());
        persistence.save(completed, patient.getId());
        CompletedCaseEntity caseEntity = completedCaseRepository.findByCaseId(completed.id()).orElseThrow();

        ClinicalDocument document = ClinicalDocument.create("doc-weird-vital", caseEntity, patient,
                "notes.pdf", "stored.pdf", "application/pdf", 1024);
        documentRepository.save(document);
        ClinicalDocumentExtraction extraction = ClinicalDocumentExtraction.create(document,
                new ExtractionOutcome(ExtractionStatus.EXTRACTED, ExtractionErrorCategory.NONE, null,
                        "report text", 1, List.of(new ExtractionResult.PageText(1, "report text"))));
        extractionRepository.save(extraction);
        StructuredFindings structured = new StructuredFindings(
                new PatientIdentifiers("Ravi Kumar", null, "Male", "MRN123"),
                new EncounterMetadata(null, null, null, null, "Notes"),
                List.of(new VitalSign(VitalType.OXYGEN_SATURATION, "unable to record", null,
                        "SpO2: unable to record", 0)),
                List.of(), List.of(), InteractionAnalysisStatus.NOT_AVAILABLE);
        findingsRepository.save(ClinicalDocumentFindings.create(extraction, structured));

        FhirBundle bundle = fhirCaseExportService.exportCompletedCase(completed.id()).orElseThrow();
        FhirObservation vital = bundle.entry().stream()
                .map(FhirEntry::resource)
                .filter(FhirObservation.class::isInstance)
                .map(FhirObservation.class::cast)
                .findFirst().orElseThrow();

        assertThat(vital.valueString()).isEqualTo("unable to record");
        assertThat(vital.valueQuantity()).isNull();
        assertThat(vital.interpretation()).isNull();
        assertThat(bundleValidator.validate(bundle)).isEmpty();
    }

    @Test
    void caseWithoutConsentsExportsWithoutConsentEntries() {
        User patient = patient();
        CompletedCase completed = CompletedCase.withNewId(buildAnswers(1), patient.getId());
        persistence.save(completed, patient.getId());

        FhirBundle bundle = fhirCaseExportService.exportCompletedCase(completed.id()).orElseThrow();

        assertThat(bundle.entry()).extracting(FhirEntry::resource)
                .extracting(FhirCaseExportServiceIntegrationTests::resourceType)
                .doesNotContain("Consent");
    }

    private List<String> collectReferences(FhirBundle bundle) {
        java.util.ArrayList<String> refs = new java.util.ArrayList<>();
        for (FhirEntry entry : bundle.entry()) {
            Object resource = entry.resource();
            if (resource instanceof in.devmedi.kiosk.module.fhir.model.FhirEncounter encounter) {
                collect(refs, encounter.subject());
            } else if (resource instanceof FhirObservation observation) {
                collect(refs, observation.subject());
                collect(refs, observation.encounter());
            } else if (resource instanceof in.devmedi.kiosk.module.fhir.model.FhirDocumentReference reference) {
                if (reference.context() != null) {
                    reference.context().encounter().forEach(e -> collect(refs, e));
                }
            } else if (resource instanceof in.devmedi.kiosk.module.fhir.model.FhirConsent consent) {
                collect(refs, consent.patient());
            }
        }
        return refs;
    }

    private void collect(java.util.ArrayList<String> refs,
                         in.devmedi.kiosk.module.fhir.model.FhirReference reference) {
        if (reference != null && reference.reference() != null && reference.reference().contains("/")) {
            refs.add(reference.reference());
        }
    }

    private static String resourceType(Object resource) {
        if (resource instanceof in.devmedi.kiosk.module.fhir.model.FhirPatient) {
            return "Patient";
        }
        if (resource instanceof in.devmedi.kiosk.module.fhir.model.FhirEncounter) {
            return "Encounter";
        }
        if (resource instanceof FhirObservation) {
            return "Observation";
        }
        if (resource instanceof in.devmedi.kiosk.module.fhir.model.FhirDocumentReference) {
            return "DocumentReference";
        }
        if (resource instanceof in.devmedi.kiosk.module.fhir.model.FhirConsent) {
            return "Consent";
        }
        throw new IllegalArgumentException("unexpected resource " + resource);
    }

    private static String resourceId(Object resource) {
        if (resource instanceof in.devmedi.kiosk.module.fhir.model.FhirPatient patient) {
            return patient.id();
        }
        if (resource instanceof FhirEncounter encounter) {
            return encounter.id();
        }
        if (resource instanceof FhirObservation observation) {
            return observation.id();
        }
        if (resource instanceof FhirDocumentReference reference) {
            return reference.id();
        }
        if (resource instanceof in.devmedi.kiosk.module.fhir.model.FhirConsent consent) {
            return consent.id();
        }
        throw new IllegalArgumentException("unexpected resource " + resource);
    }
}