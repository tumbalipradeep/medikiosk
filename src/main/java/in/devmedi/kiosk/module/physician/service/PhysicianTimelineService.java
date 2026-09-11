package in.devmedi.kiosk.module.physician.service;

import in.devmedi.kiosk.module.document.entity.ClinicalDocument;
import in.devmedi.kiosk.module.document.entity.ClinicalDocumentExtraction;
import in.devmedi.kiosk.module.document.extraction.ExtractionResult;
import in.devmedi.kiosk.module.document.extraction.ExtractionStatus;
import in.devmedi.kiosk.module.document.extraction.ExtractionSummary;
import in.devmedi.kiosk.module.document.findings.DocumentDetailResponse;
import in.devmedi.kiosk.module.document.findings.DocumentWorkspaceItem;
import in.devmedi.kiosk.module.document.findings.TimelineResponse;
import in.devmedi.kiosk.module.document.findings.entity.ClinicalDocumentFindings;
import in.devmedi.kiosk.module.document.findings.entity.ClinicalDocumentFindingsLab;
import in.devmedi.kiosk.module.document.findings.entity.ClinicalDocumentFindingsMedication;
import in.devmedi.kiosk.module.document.findings.entity.ClinicalDocumentFindingsVital;
import in.devmedi.kiosk.module.document.findings.model.AbnormalityStatus;
import in.devmedi.kiosk.module.document.findings.model.CompletenessStatus;
import in.devmedi.kiosk.module.document.findings.model.EncounterMetadata;
import in.devmedi.kiosk.module.document.findings.model.InteractionAnalysisStatus;
import in.devmedi.kiosk.module.document.findings.model.LabResult;
import in.devmedi.kiosk.module.document.findings.model.Medication;
import in.devmedi.kiosk.module.document.findings.model.MedicationField;
import in.devmedi.kiosk.module.document.findings.model.PatientIdentifiers;
import in.devmedi.kiosk.module.document.findings.model.TimelineEvent;
import in.devmedi.kiosk.module.document.findings.model.TimelineEventType;
import in.devmedi.kiosk.module.document.findings.model.VitalSign;
import in.devmedi.kiosk.module.document.findings.repository.ClinicalDocumentFindingsRepository;
import in.devmedi.kiosk.module.document.repository.ClinicalDocumentExtractionRepository;
import in.devmedi.kiosk.module.document.repository.ClinicalDocumentRepository;
import in.devmedi.kiosk.module.physician.entity.CompletedCaseEntity;
import in.devmedi.kiosk.module.physician.repository.CompletedCaseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Derives the physician document workspace and clinical timeline from existing
 * persisted document, extraction, and findings data. No new tables are created;
 * every output is deterministic and repeatable from the database state alone.
 *
 * <p>Conflicting findings from different documents are never merged or resolved;
 * each observation is surfaced as a separate timeline event with its own source
 * document and snippet. Identical findings from multiple documents are likewise
 * preserved separately.</p>
 */
@Service
public class PhysicianTimelineService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT);

    private final CompletedCaseRepository completedCaseRepository;
    private final ClinicalDocumentRepository documentRepository;
    private final ClinicalDocumentExtractionRepository extractionRepository;
    private final ClinicalDocumentFindingsRepository findingsRepository;

    public PhysicianTimelineService(CompletedCaseRepository completedCaseRepository,
                                    ClinicalDocumentRepository documentRepository,
                                    ClinicalDocumentExtractionRepository extractionRepository,
                                    ClinicalDocumentFindingsRepository findingsRepository) {
        this.completedCaseRepository = completedCaseRepository;
        this.documentRepository = documentRepository;
        this.extractionRepository = extractionRepository;
        this.findingsRepository = findingsRepository;
    }

    /**
     * Verifies that the case exists. Completed cases are attached to the
     * patient who submitted the intake, so no physician-user match is applied;
     * access is instead gated by {@code ROLE_PHYSICIAN} on the route plus the
     * case/document ownership bindings enforced by the service methods.
     */
    public CompletedCaseEntity verifyCaseExists(String caseId) {
        return completedCaseRepository.findByCaseId(caseId)
                .orElseThrow(() -> new IllegalArgumentException("Case not found"));
    }

    // ─── Document workspace ───────────────────────────────────────────

    /**
     * Builds the physician document workspace rows for a case: metadata plus
     * deterministically derived findings counts. Documents are ordered by
     * upload time (the same order the timeline uses).
     */
    @Transactional(readOnly = true)
    public List<DocumentWorkspaceItem> workspaceItems(String caseId) {
        List<ClinicalDocument> documents =
                documentRepository.findByCompletedCase_CaseIdOrderByUploadedAtAsc(caseId);
        List<ClinicalDocumentExtraction> extractions =
                extractionRepository.findByDocumentIn(documents);

        Map<String, ClinicalDocumentExtraction> extractionByDocId = extractions.stream()
                .collect(Collectors.toMap(e -> e.getDocument().getDocumentId(), e -> e));

        List<DocumentWorkspaceItem> items = new ArrayList<>();
        for (ClinicalDocument doc : documents) {
            ClinicalDocumentExtraction extraction = extractionByDocId.get(doc.getDocumentId());
            ExtractionSummary summary = extraction != null
                    ? ExtractionSummary.from(extraction)
                    : ExtractionSummary.pending(doc.getDocumentId());

            boolean hasFindings = false;
            int labCount = 0;
            int abnormalLabCount = 0;
            int medicationCount = 0;
            int duplicateMedCount = 0;

            if (extraction != null) {
                Optional<ClinicalDocumentFindings> findingsOpt =
                        findingsRepository.findByExtraction(extraction);
                if (findingsOpt.isPresent()) {
                    ClinicalDocumentFindings findings = findingsOpt.get();
                    hasFindings = true;
                    labCount = findings.getLabResults().size();
                    abnormalLabCount = (int) findings.getLabResults().stream()
                            .map(ClinicalDocumentFindingsLab::getAbnormalityStatus)
                            .filter(s -> "LOW".equals(s) || "HIGH".equals(s))
                            .count();
                    medicationCount = findings.getMedications().size();
                    duplicateMedCount = (int) findings.getMedications().stream()
                            .filter(m -> m.getDuplicateCount() > 1)
                            .map(ClinicalDocumentFindingsMedication::getName)
                            .distinct()
                            .count();
                }
            }

            items.add(new DocumentWorkspaceItem(
                    doc.getDocumentId(),
                    doc.getOriginalFilename(),
                    doc.getContentType(),
                    doc.getFileSize(),
                    doc.getUploadedAt(),
                    summary.status(),
                    summary.statusLabel(),
                    summary.badgeClass(),
                    hasFindings,
                    labCount,
                    abnormalLabCount,
                    medicationCount,
                    duplicateMedCount));
        }
        return List.copyOf(items);
    }

    // ─── Document detail ──────────────────────────────────────────────

    /**
     * Full physician-facing detail view for one document. Rejects documents
     * that do not belong to the given case.
     */
    @Transactional(readOnly = true)
    public DocumentDetailResponse documentDetail(String caseId, String documentId) {
        ClinicalDocument doc = documentRepository.findByDocumentId(documentId)
                .filter(d -> d.getCompletedCase().getCaseId().equals(caseId))
                .orElseThrow(() -> new IllegalArgumentException("Document not found for the given case"));

        ExtractionSummary summary = ExtractionSummary.pending(documentId);
        PatientIdentifiers patient = null;
        EncounterMetadata encounter = null;
        List<VitalSign> vitals = List.of();
        List<LabResult> labResults = List.of();
        List<Medication> medications = List.of();
        InteractionAnalysisStatus interactionStatus = InteractionAnalysisStatus.NOT_AVAILABLE;
        Instant findingsCreatedAt = null;

        Optional<ClinicalDocumentExtraction> extractionOpt =
                extractionRepository.findByDocument(doc);
        if (extractionOpt.isPresent()) {
            ClinicalDocumentExtraction extraction = extractionOpt.get();
            summary = ExtractionSummary.from(extraction);

            Optional<ClinicalDocumentFindings> findingsOpt =
                    findingsRepository.findByExtraction(extraction);
            if (findingsOpt.isPresent()) {
                ClinicalDocumentFindings findings = findingsOpt.get();
                findingsCreatedAt = findings.getCreatedAt();

                if (findings.getPatientName() != null) {
                    patient = new PatientIdentifiers(findings.getPatientName(), findings.getPatientDob(),
                            findings.getPatientSex(), findings.getPatientMrn());
                }
                if (findings.getReportDate() != null) {
                    encounter = new EncounterMetadata(findings.getReportDate(), findings.getEncounterDate(),
                            findings.getFacility(), findings.getClinician(), findings.getReportType());
                }

                vitals = findings.getVitals().stream()
                        .map(v -> new VitalSign(v.getType(), v.getValue(), v.getUnit(),
                                v.getSourceSnippet(), v.getOccurrenceIndex()))
                        .toList();
                labResults = findings.getLabResults().stream()
                        .map(l -> new LabResult(l.getTestName(), l.getRawValue(), l.getValue(),
                                l.getUnit(), l.getReferenceRange(), l.getSpecimenDate(),
                                l.getSourceSnippet(), l.getOccurrenceIndex(),
                                statusOf(AbnormalityStatus.class, l.getAbnormalityStatus(),
                                        AbnormalityStatus.UNKNOWN)))
                        .toList();
                medications = findings.getMedications().stream()
                        .map(m -> new Medication(m.getName(), m.getStrength(), m.getDose(),
                                m.getRoute(), m.getFrequency(), m.getDuration(),
                                m.getSourceSnippet(), m.getOccurrenceIndex(),
                                statusOf(CompletenessStatus.class, m.getCompletenessStatus(),
                                        CompletenessStatus.INCOMPLETE),
                                missingFieldsOf(m.getMissingFields()), m.getDuplicateCount()))
                        .toList();
                interactionStatus = statusOf(InteractionAnalysisStatus.class,
                        findings.getInteractionAnalysisStatus(), InteractionAnalysisStatus.NOT_AVAILABLE);
            }
        }

        List<ExtractionResult.PageText> pages;
        if (summary.status() == ExtractionStatus.EXTRACTED && extractionOpt.isPresent()) {
            pages = ExtractionResult.from(extractionOpt.get(), documentId).pages();
        } else {
            pages = List.of();
        }

        return new DocumentDetailResponse(
                documentId,
                doc.getOriginalFilename(),
                doc.getContentType(),
                doc.getFileSize(),
                doc.getUploadedAt(),
                summary.status(),
                summary.statusLabel(),
                summary.badgeClass(),
                summary.errorCategory(),
                summary.errorMessage(),
                summary.extractedAt(),
                summary.pageCount(),
                pages,
                patient,
                encounter,
                vitals,
                labResults,
                medications,
                interactionStatus,
                findingsCreatedAt);
    }

    // ─── Clinical timeline ────────────────────────────────────────────

    /**
     * Derives the full clinical timeline for a case.
     *
     * <p>Ordering contract: dated events (report date, encounter date) appear
     * first, chronological by their explicit date. Events without a clinical
     * date (document uploads, vitals, lab results, medications) appear after,
     * grouped in document upload order then occurrence order. Within a single
     * date, events keep document order and then occurrence order. No dates are
     * ever invented.</p>
     */
    @Transactional(readOnly = true)
    public TimelineResponse timeline(String caseId) {
        List<ClinicalDocument> documents =
                documentRepository.findByCompletedCase_CaseIdOrderByUploadedAtAsc(caseId);
        List<ClinicalDocumentExtraction> extractions =
                extractionRepository.findByDocumentIn(documents);

        Map<String, ClinicalDocumentExtraction> extractionByDocId = extractions.stream()
                .collect(Collectors.toMap(e -> e.getDocument().getDocumentId(), e -> e));

        Map<String, Integer> documentOrder = new HashMap<>();
        for (int i = 0; i < documents.size(); i++) {
            documentOrder.put(documents.get(i).getDocumentId(), i);
        }

        List<OrderedEvent> collected = new ArrayList<>();
        for (ClinicalDocument doc : documents) {
            int order = documentOrder.get(doc.getDocumentId());
            ClinicalDocumentExtraction extraction = extractionByDocId.get(doc.getDocumentId());

            collected.add(new OrderedEvent(order, new TimelineEvent(
                    TimelineEventType.DOCUMENT_UPLOADED,
                    null,
                    "Document uploaded: " + doc.getOriginalFilename(),
                    doc.getContentType() + " \u00B7 " + formatSize(doc.getFileSize()),
                    doc.getDocumentId(),
                    doc.getOriginalFilename(),
                    null,
                    doc.getUploadedAt(),
                    0)));

            if (extraction != null) {
                Optional<ClinicalDocumentFindings> findingsOpt =
                        findingsRepository.findByExtraction(extraction);
                if (findingsOpt.isPresent()) {
                    addFindingsEvents(collected, order, findingsOpt.get(), doc, doc.getUploadedAt());
                }
            }
        }

        collected.sort(Comparator
                .comparing((OrderedEvent e) -> !e.isDated())
                .thenComparing(Comparator.nullsLast(
                        Comparator.comparing(e -> e.event().sortInstant())))
                .thenComparingInt(OrderedEvent::documentOrder)
                .thenComparingInt(e -> e.event().occurrenceIndex()));

        return new TimelineResponse(collected.stream().map(OrderedEvent::event).toList());
    }

    private void addFindingsEvents(List<OrderedEvent> collected,
                                   int documentOrder,
                                   ClinicalDocumentFindings findings,
                                   ClinicalDocument doc,
                                   Instant uploadTime) {
        String documentId = doc.getDocumentId();
        String filename = doc.getOriginalFilename();

        if (findings.getReportDate() != null) {
            collected.add(new OrderedEvent(documentOrder, new TimelineEvent(
                    TimelineEventType.REPORT_DATE,
                    findings.getReportDate(),
                    "Report date: " + findings.getReportDate(),
                    findings.getReportType() != null ? findings.getReportType() : null,
                    documentId,
                    filename,
                    null,
                    dateInstant(findings.getReportDate()),
                    1)));
        }

        if (findings.getEncounterDate() != null && !findings.getEncounterDate().equals(findings.getReportDate())) {
            collected.add(new OrderedEvent(documentOrder, new TimelineEvent(
                    TimelineEventType.ENCOUNTER_DATE,
                    findings.getEncounterDate(),
                    "Encounter date: " + findings.getEncounterDate(),
                    findings.getFacility(),
                    documentId,
                    filename,
                    null,
                    dateInstant(findings.getEncounterDate()),
                    2)));
        }

        int base = 100;
        for (ClinicalDocumentFindingsVital vital : findings.getVitals()) {
            collected.add(new OrderedEvent(documentOrder, new TimelineEvent(
                    TimelineEventType.VITALS,
                    null,
                    vital.getType().name().replace('_', ' '),
                    vital.getValue() + (vital.getUnit() != null ? " " + vital.getUnit() : ""),
                    documentId,
                    filename,
                    vital.getSourceSnippet(),
                    uploadTime,
                    base + vital.getOccurrenceIndex())));
        }

        for (ClinicalDocumentFindingsLab lab : findings.getLabResults()) {
            StringBuilder detail = new StringBuilder();
            if (lab.getValue() != null) {
                detail.append(lab.getValue());
            }
            if (lab.getRawValue() != null && !lab.getRawValue().equals(lab.getValue())) {
                detail.append(" (as printed: ").append(lab.getRawValue()).append(')');
            }
            if (lab.getUnit() != null) {
                detail.append(' ').append(lab.getUnit());
            }
            if (lab.getSpecimenDate() != null) {
                detail.append(" \u00B7 specimen ").append(lab.getSpecimenDate());
            }
            detail.append(" \u00B7 ").append(lab.getAbnormalityStatus());
            collected.add(new OrderedEvent(documentOrder, new TimelineEvent(
                    TimelineEventType.LAB_RESULT,
                    null,
                    lab.getTestName(),
                    detail.toString(),
                    documentId,
                    filename,
                    lab.getSourceSnippet(),
                    uploadTime,
                    base + lab.getOccurrenceIndex())));
        }

        for (ClinicalDocumentFindingsMedication med : findings.getMedications()) {
            StringBuilder detail = new StringBuilder();
            if (med.getStrength() != null) {
                detail.append(med.getStrength());
            }
            if (med.getDose() != null) {
                if (!detail.isEmpty()) {
                    detail.append(" \u00B7 ");
                }
                detail.append(med.getDose());
            }
            if (med.getFrequency() != null) {
                if (!detail.isEmpty()) {
                    detail.append(" \u00B7 ");
                }
                detail.append(med.getFrequency());
            }
            if (med.getDuration() != null) {
                if (!detail.isEmpty()) {
                    detail.append(" \u00B7 ");
                }
                detail.append(med.getDuration());
            }
            if (med.getDuplicateCount() > 1) {
                detail.append(" \u00B7 \u00D7").append(med.getDuplicateCount());
            }
            collected.add(new OrderedEvent(documentOrder, new TimelineEvent(
                    TimelineEventType.MEDICATION,
                    null,
                    med.getName(),
                    detail.isEmpty() ? null : detail.toString(),
                    documentId,
                    filename,
                    med.getSourceSnippet(),
                    uploadTime,
                    base + med.getOccurrenceIndex())));
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────

    private Instant dateInstant(String dateStr) {
        LocalDate date = parseDate(dateStr);
        return date != null ? date.atStartOfDay(ZoneOffset.UTC).toInstant() : null;
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(dateStr, DATE_FMT);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        }
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private <E extends Enum<E>> E statusOf(Class<E> type, String value, E fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private List<MedicationField> missingFieldsOf(String joined) {
        if (joined == null || joined.isBlank()) {
            return List.of();
        }
        return Arrays.stream(joined.split(","))
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .map(token -> statusOf(MedicationField.class, token, null))
                .filter(Objects::nonNull)
                .toList();
    }

    private record OrderedEvent(int documentOrder, TimelineEvent event) {
        boolean isDated() {
            return event.eventDate() != null;
        }
    }
}