package in.devmedi.kiosk.module.fhir.service;

import in.devmedi.kiosk.module.auth.entity.User;
import in.devmedi.kiosk.module.consent.entity.Consent;
import in.devmedi.kiosk.module.consent.repository.ConsentRepository;
import in.devmedi.kiosk.module.document.entity.ClinicalDocument;
import in.devmedi.kiosk.module.document.entity.ClinicalDocumentExtraction;
import in.devmedi.kiosk.module.document.findings.entity.ClinicalDocumentFindings;
import in.devmedi.kiosk.module.document.findings.repository.ClinicalDocumentFindingsRepository;
import in.devmedi.kiosk.module.document.repository.ClinicalDocumentExtractionRepository;
import in.devmedi.kiosk.module.document.repository.ClinicalDocumentRepository;
import in.devmedi.kiosk.module.fhir.id.FhirIds;
import in.devmedi.kiosk.module.fhir.mapping.FhirAnswerMapper;
import in.devmedi.kiosk.module.fhir.mapping.FhirConsentMapper;
import in.devmedi.kiosk.module.fhir.mapping.FhirContextRefs;
import in.devmedi.kiosk.module.fhir.mapping.FhirDateTimes;
import in.devmedi.kiosk.module.fhir.mapping.FhirDocumentMapper;
import in.devmedi.kiosk.module.fhir.mapping.FhirEncounterMapper;
import in.devmedi.kiosk.module.fhir.mapping.FhirPatientMapper;
import in.devmedi.kiosk.module.fhir.model.FhirBundle;
import in.devmedi.kiosk.module.fhir.model.FhirEntry;
import in.devmedi.kiosk.module.fhir.model.FhirEncounter;
import in.devmedi.kiosk.module.fhir.model.FhirPatient;
import in.devmedi.kiosk.module.fhir.model.FhirReference;
import in.devmedi.kiosk.module.fhir.validation.FhirBundleValidator;
import in.devmedi.kiosk.module.physician.entity.CompletedCaseAnswerEntity;
import in.devmedi.kiosk.module.physician.entity.CompletedCaseEntity;
import in.devmedi.kiosk.module.physician.repository.CompletedCaseAnswerRepository;
import in.devmedi.kiosk.module.physician.repository.CompletedCaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Read-only FHIR R4 projection of one completed clinical case.
 *
 * <p>Assembles a deterministic {@code collection} bundle for a persisted case:
 * the kiosk {@code Patient}, the case {@code Encounter}, one {@code Observation}
 * per captured clinical answer, one {@code DocumentReference} plus the decoded
 * findings observations for each document uploaded to the case, and one
 * {@code Consent} per granted/revoked consent of the patient. Bundle entry
 * order is fixed (Patient, Encounter, answers in answering order, per document
 * in upload order then the reference, vitals and labs in occurrence order, and
 * consents by consent type), every logical id is a deterministic function of
 * the persisted business key, and every internal reference resolves to a
 * bundle entry's {@code fullUrl}.</p>
 *
 * <p>M4.2 hardening: the bundle {@code timestamp} is derived from the persisted
 * case creation time (not wall clock) so identical persisted data always
 * renders byte-for-byte identical bundles; document ties on {@code uploadedAt}
 * are broken by {@code documentId}; and assembled entries are de-duplicated by
 * logical id (first occurrence wins) so an anomalous data set can never yield
 * duplicate resources. The assembled bundle is structurally validated
 * (unique ids, resolvable references, required elements) and any issue is
 * logged without blocking the read-only export.</p>
 */
@Service
public class FhirCaseExportService {

    private static final Logger log = LoggerFactory.getLogger(FhirCaseExportService.class);

    /**
     * Deterministic document order for the exported bundle: {@code uploadedAt}
     * ascending with {@code null} values last, then {@code documentId} ascending
     * to break upload-time ties (documents uploaded in the same instant). Kept
     * package-visible so export ordering is directly testable.
     */
    static final Comparator<ClinicalDocument> DOCUMENT_ORDER = Comparator
            .comparing(ClinicalDocument::getUploadedAt,
                    Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(ClinicalDocument::getDocumentId);

    private final CompletedCaseRepository completedCaseRepository;
    private final CompletedCaseAnswerRepository answerRepository;
    private final ClinicalDocumentRepository documentRepository;
    private final ClinicalDocumentExtractionRepository extractionRepository;
    private final ClinicalDocumentFindingsRepository findingsRepository;
    private final ConsentRepository consentRepository;
    private final FhirBundleValidator bundleValidator;

    public FhirCaseExportService(CompletedCaseRepository completedCaseRepository,
                                 CompletedCaseAnswerRepository answerRepository,
                                 ClinicalDocumentRepository documentRepository,
                                 ClinicalDocumentExtractionRepository extractionRepository,
                                 ClinicalDocumentFindingsRepository findingsRepository,
                                 ConsentRepository consentRepository,
                                 FhirBundleValidator bundleValidator) {
        this.completedCaseRepository = completedCaseRepository;
        this.answerRepository = answerRepository;
        this.documentRepository = documentRepository;
        this.extractionRepository = extractionRepository;
        this.findingsRepository = findingsRepository;
        this.consentRepository = consentRepository;
        this.bundleValidator = bundleValidator;
    }

    @Transactional(readOnly = true)
    public Optional<FhirBundle> exportCompletedCase(String caseId) {
        Optional<CompletedCaseEntity> maybeCase = completedCaseRepository.findByCaseId(caseId);
        if (maybeCase.isEmpty()) {
            return Optional.empty();
        }
        CompletedCaseEntity completedCase = maybeCase.get();
        List<FhirEntry> entries = new ArrayList<>();

        User patient = completedCase.getUser();
        FhirContextRefs refs = addPatientAndEncounter(entries, completedCase, patient);

        List<CompletedCaseAnswerEntity> answers =
                answerRepository.findByCaseIdOrderByAnswerOrder(caseId);
        for (CompletedCaseAnswerEntity answer : answers) {
            entries.add(entry(FhirAnswerMapper.toFhir(answer, caseId, refs)));
        }

        List<ClinicalDocument> documents = new ArrayList<>(
                documentRepository.findByCompletedCase_CaseIdOrderByUploadedAtAsc(caseId));
        documents.sort(DOCUMENT_ORDER);
        for (ClinicalDocument document : documents) {
            addDocument(entries, document, refs);
        }

        if (patient != null) {
            for (Consent consent : consentRepository.findByUserIdOrderByConsentType(patient.getId())) {
                entries.add(entry(FhirConsentMapper.toFhir(patient, consent)));
            }
        }

        List<FhirEntry> deduplicated = deduplicate(entries);
        FhirBundle bundle = FhirBundle.collection(
                FhirIds.logicalId("Bundle", caseId),
                FhirDateTimes.instant(completedCase.getCreatedAt()),
                deduplicated);

        List<String> issues = bundleValidator.validate(bundle);
        for (String issue : issues) {
            log.warn("fhir.bundle.issue caseId={} issue={}", caseId, issue);
        }
        return Optional.of(bundle);
    }

    /** Deterministic logical-id based de-duplication, first occurrence wins. */
    private List<FhirEntry> deduplicate(List<FhirEntry> entries) {
        Set<String> seen = new HashSet<>();
        List<FhirEntry> result = new ArrayList<>(entries.size());
        for (FhirEntry candidate : entries) {
            String id = resourceId(candidate.resource());
            if (seen.add(id)) {
                result.add(candidate);
            } else {
                log.warn("fhir.bundle.duplicate resourceId={}", id);
            }
        }
        return result;
    }

    private String resourceId(Object resource) {
        return switch (resource) {
            case FhirPatient p -> p.id();
            case FhirEncounter e -> e.id();
            case in.devmedi.kiosk.module.fhir.model.FhirObservation o -> o.id();
            case in.devmedi.kiosk.module.fhir.model.FhirDocumentReference d -> d.id();
            case in.devmedi.kiosk.module.fhir.model.FhirConsent c -> c.id();
            default -> throw new IllegalArgumentException(
                    "unsupported FHIR resource: " + resource.getClass());
        };
    }

    private FhirContextRefs addPatientAndEncounter(List<FhirEntry> entries,
                                                   CompletedCaseEntity completedCase,
                                                   User patient) {
        FhirReference subject = null;
        if (patient != null) {
            FhirPatient fhirPatient = FhirPatientMapper.toFhir(patient);
            String patientUrl = FhirIds.fullUrl(fhirPatient.id());
            entries.add(entry(fhirPatient));
            subject = FhirReference.of("Patient/" + fhirPatient.id(), patient.getDisplayName());
        }
        FhirEncounter encounter = FhirEncounterMapper.toFhir(completedCase, subject);
        entries.add(entry(encounter));
        return new FhirContextRefs(
                subject == null ? null : subject.reference(),
                "Encounter/" + encounter.id());
    }

    private void addDocument(List<FhirEntry> entries, ClinicalDocument document, FhirContextRefs refs) {
        ClinicalDocumentExtraction extraction = extractionRepository.findByDocument(document).orElse(null);
        ClinicalDocumentFindings findings = extraction == null
                ? null : findingsRepository.findByExtraction(extraction).orElse(null);

        String reportType = findings == null ? null : findings.getReportType();
        entries.add(entry(FhirDocumentMapper.toDocumentReference(document, reportType, refs.encounterReference())));

        if (findings == null) {
            return;
        }
        for (var vital : findings.getVitals()) {
            entries.add(entry(FhirDocumentMapper.toVitalObservation(document, vital, refs)));
        }
        for (var lab : findings.getLabResults()) {
            entries.add(entry(FhirDocumentMapper.toLabObservation(document, lab, refs)));
        }
    }

    private FhirEntry entry(Object resource) {
        String id = switch (resource) {
            case FhirPatient p -> p.id();
            case FhirEncounter e -> e.id();
            case in.devmedi.kiosk.module.fhir.model.FhirObservation o -> o.id();
            case in.devmedi.kiosk.module.fhir.model.FhirDocumentReference d -> d.id();
            case in.devmedi.kiosk.module.fhir.model.FhirConsent c -> c.id();
            default -> throw new IllegalArgumentException("unsupported FHIR resource: " + resource.getClass());
        };
        return FhirEntry.of(FhirIds.fullUrl(id), resource);
    }
}