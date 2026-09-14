package in.devmedi.kiosk.module.medication.service;

import in.devmedi.kiosk.module.clinical.history.ClinicalHistoryCategory;
import in.devmedi.kiosk.module.clinical.history.ClinicalHistoryItem;
import in.devmedi.kiosk.module.clinical.history.ClinicalHistoryService;
import in.devmedi.kiosk.module.document.entity.ClinicalDocument;
import in.devmedi.kiosk.module.document.entity.ClinicalDocumentExtraction;
import in.devmedi.kiosk.module.document.findings.entity.ClinicalDocumentFindings;
import in.devmedi.kiosk.module.document.findings.entity.ClinicalDocumentFindingsMedication;
import in.devmedi.kiosk.module.document.findings.repository.ClinicalDocumentFindingsRepository;
import in.devmedi.kiosk.module.document.repository.ClinicalDocumentExtractionRepository;
import in.devmedi.kiosk.module.document.repository.ClinicalDocumentRepository;
import in.devmedi.kiosk.module.medication.entity.CaseMedication;
import in.devmedi.kiosk.module.medication.entity.MedicationInteractionRule;
import in.devmedi.kiosk.module.medication.entity.MedicationProfile;
import in.devmedi.kiosk.module.medication.enums.MedicationSource;
import in.devmedi.kiosk.module.medication.repository.CaseMedicationRepository;
import in.devmedi.kiosk.module.medication.repository.MedicationInteractionRuleRepository;
import in.devmedi.kiosk.module.medication.repository.MedicationProfileRepository;
import in.devmedi.kiosk.module.physician.service.CompletedCase;
import in.devmedi.kiosk.module.physician.service.CompletedCasePersistenceService;
import in.devmedi.kiosk.module.physician.workspace.CaseNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Assembles a case's medication screen and applies the physician overlay.
 *
 * <p>Sourced medicines (patient-history answers and document-finding
 * medications) are always re-derived, never copied: the screen stays honest as
 * the underlying history or findings change. The {@code case_medications} table
 * holds only the physician's overlay — medicines added to the case and
 * name-based suppression tombstones. Suppression is a tombstone (soft), so a
 * physician decision can be reviewed and reversed.</p>
 *
 * <p>This is a screening aid: it surfaces known pairings for the physician to
 * review. It never prescribes, doses, or diagnoses.</p>
 */
@Service
public class MedicationInteractionService {

    private final CompletedCasePersistenceService casePersistence;
    private final ClinicalHistoryService historyService;
    private final ClinicalDocumentRepository documentRepository;
    private final ClinicalDocumentExtractionRepository extractionRepository;
    private final ClinicalDocumentFindingsRepository findingsRepository;
    private final MedicationProfileRepository profileRepository;
    private final MedicationInteractionRuleRepository ruleRepository;
    private final CaseMedicationRepository caseMedicationRepository;
    private final MedicationNormalizer normalizer;
    private final MedicationInteractionEngine engine;

    public MedicationInteractionService(CompletedCasePersistenceService casePersistence,
                                        ClinicalHistoryService historyService,
                                        ClinicalDocumentRepository documentRepository,
                                        ClinicalDocumentExtractionRepository extractionRepository,
                                        ClinicalDocumentFindingsRepository findingsRepository,
                                        MedicationProfileRepository profileRepository,
                                        MedicationInteractionRuleRepository ruleRepository,
                                        CaseMedicationRepository caseMedicationRepository,
                                        MedicationNormalizer normalizer,
                                        MedicationInteractionEngine engine) {
        this.casePersistence = casePersistence;
        this.historyService = historyService;
        this.documentRepository = documentRepository;
        this.extractionRepository = extractionRepository;
        this.findingsRepository = findingsRepository;
        this.profileRepository = profileRepository;
        this.ruleRepository = ruleRepository;
        this.caseMedicationRepository = caseMedicationRepository;
        this.normalizer = normalizer;
        this.engine = engine;
    }

    @Transactional(readOnly = true)
    public MedicationInteractionReport report(String caseId) {
        CompletedCase completed = requireCase(caseId);
        List<MedicationProfile> profiles = profileRepository.findAll();
        List<MedicationInteractionRule> rules = ruleRepository.findAll();

        Map<String, Entry> byName = new LinkedHashMap<>();
        List<CaseMedication> overlay = caseMedicationRepository.findByCaseIdOrderByMedNameAsc(caseId);
        java.util.Set<String> suppressed = new java.util.HashSet<>();
        for (CaseMedication row : overlay) {
            if (row.getSuppressedAt() != null) {
                suppressed.add(row.getMedName().toLowerCase());
            }
        }

        List<Entry> physicianEntries = new ArrayList<>();
        List<Entry> sourcedEntries = new ArrayList<>();

        for (CaseMedication row : overlay) {
            if (row.getSuppressedAt() != null) {
                continue;
            }
            DetectedMedication dm = normalizer.resolveSingle(row.getMedName(), profiles);
            if (dm != null) {
                physicianEntries.add(new Entry(dm, row.getId(), row.getDose(), row.getFrequency()));
            }
        }

        if (completed.userId() != null) {
            Long patientId = completed.userId();
            for (ClinicalHistoryItem item : historyService.itemsByCategory(patientId, ClinicalHistoryCategory.MEDICATIONS)) {
                for (DetectedMedication dm : normalizer.resolveFreeText(item.getValue(), profiles)) {
                    sourcedEntries.add(new Entry(withSource(dm, MedicationSource.PATIENT_HISTORY), null, null, null));
                }
            }
            List<ClinicalDocument> documents =
                    documentRepository.findByCompletedCase_CaseIdOrderByUploadedAtAsc(caseId);
            if (!documents.isEmpty()) {
                List<ClinicalDocumentExtraction> extractions = extractionRepository.findByDocumentIn(documents);
                for (ClinicalDocumentExtraction extraction : extractions) {
                    findingsRepository.findByExtraction(extraction).ifPresent(findings ->
                            collectFindingsMedications(findings, sourcedEntries, profiles));
                }
            }
        }

        List<Entry> ordered = new ArrayList<>(physicianEntries);
        ordered.addAll(sourcedEntries);

        for (Entry entry : ordered) {
            if (entry.dm.displayName() == null || entry.dm.displayName().isBlank()) {
                continue;
            }
            if (suppressed.contains(entry.dm.displayName().toLowerCase())) {
                continue;
            }
            byName.putIfAbsent(entry.dm.displayName().toLowerCase(), entry);
        }

        List<Pair> pairs = new ArrayList<>();
        for (Entry entry : byName.values()) {
            pairs.add(new Pair(entry.dm,
                    new MedicationView(
                            entry.rowId,
                            entry.dm.displayName(),
                            entry.dm.classKey(),
                            entry.dose,
                            entry.frequency,
                            entry.dm.source(),
                            entry.rowId != null)));
        }
        pairs.sort(java.util.Comparator
                .comparing((Pair p) -> p.view.physicianAdded(), java.util.Comparator.reverseOrder())
                .thenComparing(p -> p.view.displayName(), String.CASE_INSENSITIVE_ORDER));

        List<DetectedMedication> detected = new ArrayList<>();
        List<MedicationView> views = new ArrayList<>();
        for (Pair pair : pairs) {
            detected.add(pair.dm);
            views.add(pair.view);
        }

        List<MedicationInteractionView> interactions = engine.analyze(detected, rules);
        return new MedicationInteractionReport(
                caseId,
                views,
                interactions,
                MedicationInteractionEngine.overall(interactions),
                OffsetDateTime.now());
    }

    private void collectFindingsMedications(ClinicalDocumentFindings findings,
                                            List<Entry> sourcedEntries,
                                            List<MedicationProfile> profiles) {
        for (ClinicalDocumentFindingsMedication med : findings.getMedications()) {
            DetectedMedication dm = normalizer.resolveSingle(med.getName(), profiles);
            if (dm != null) {
                sourcedEntries.add(new Entry(withSource(dm, MedicationSource.DOCUMENT_FINDING), null, null, null));
            }
        }
    }

    private static DetectedMedication withSource(DetectedMedication dm, MedicationSource source) {
        return new DetectedMedication(dm.sourceChunk(), dm.displayName(), dm.canonicalName(), dm.classKey(), source);
    }

    @Transactional
    public MedicationInteractionReport add(String caseId, String medName, String dose, String frequency,
                                           Long physicianId) {
        requireCase(caseId);
        if (medName == null || medName.isBlank()) {
            throw new IllegalArgumentException("Medicine name is required.");
        }
        List<MedicationProfile> profiles = profileRepository.findAll();
        DetectedMedication dm = normalizer.resolveSingle(medName, profiles);
        String stored = dm != null && dm.displayName() != null ? dm.displayName() : medName.trim();
        CaseMedication row = caseMedicationRepository.findByCaseIdAndMedNameIgnoreCase(caseId, stored).orElse(null);
        OffsetDateTime now = OffsetDateTime.now();
        if (row == null) {
            row = CaseMedication.add(caseId, stored, trimToNull(dose), trimToNull(frequency), physicianId, now);
        } else {
            row.reactivate(trimToNull(dose), trimToNull(frequency), now);
        }
        caseMedicationRepository.save(row);
        return report(caseId);
    }

    @Transactional
    public MedicationInteractionReport suppress(String caseId, String medName, Long physicianId) {
        requireCase(caseId);
        String stored = normalizeOverlayName(medName);
        CaseMedication row = caseMedicationRepository.findByCaseIdAndMedNameIgnoreCase(caseId, stored).orElse(null);
        OffsetDateTime now = OffsetDateTime.now();
        if (row == null) {
            row = CaseMedication.tombstone(caseId, stored, physicianId, now);
        } else {
            row.suppress(now);
        }
        caseMedicationRepository.save(row);
        return report(caseId);
    }

    @Transactional
    public MedicationInteractionReport restore(String caseId, String medName, Long physicianId) {
        requireCase(caseId);
        String stored = normalizeOverlayName(medName);
        caseMedicationRepository.findByCaseIdAndMedNameIgnoreCase(caseId, stored)
                .ifPresent(row -> {
                    row.restore();
                    caseMedicationRepository.save(row);
                });
        return report(caseId);
    }

    private String normalizeOverlayName(String medName) {
        if (medName == null || medName.isBlank()) {
            throw new IllegalArgumentException("Medicine name is required.");
        }
        List<MedicationProfile> profiles = profileRepository.findAll();
        DetectedMedication dm = normalizer.resolveSingle(medName, profiles);
        return dm != null && dm.displayName() != null ? dm.displayName() : medName.trim();
    }

    private CompletedCase requireCase(String caseId) {
        return casePersistence.findByCaseId(caseId)
                .orElseThrow(() -> new CaseNotFoundException(caseId));
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().substring(0, Math.min(value.trim().length(), 150));
    }

    private record Entry(DetectedMedication dm, Long rowId, String dose, String frequency) {
    }

    private record Pair(DetectedMedication dm, MedicationView view) {
    }
}