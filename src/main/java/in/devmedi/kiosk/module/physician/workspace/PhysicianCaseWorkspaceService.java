package in.devmedi.kiosk.module.physician.workspace;

import in.devmedi.kiosk.module.audit.entity.AuditEvent;
import in.devmedi.kiosk.module.audit.repository.AuditEventRepository;
import in.devmedi.kiosk.module.clinical.dialogue.AnswerSource;
import in.devmedi.kiosk.module.clinical.dialogue.ClinicalAnswer;
import in.devmedi.kiosk.module.clinical.dialogue.QuestionSource;
import in.devmedi.kiosk.module.clinical.redflag.RedFlag;
import in.devmedi.kiosk.module.clinical.redflag.RedFlagEvaluator;
import in.devmedi.kiosk.module.clinical.redflag.RedFlagSeverity;
import in.devmedi.kiosk.module.consent.entity.Consent;
import in.devmedi.kiosk.module.consent.repository.ConsentRepository;
import in.devmedi.kiosk.module.document.findings.DocumentWorkspaceItem;
import in.devmedi.kiosk.module.document.repository.ClinicalDocumentRepository;
import in.devmedi.kiosk.module.his.HisIntegrationBoundary;
import in.devmedi.kiosk.module.physician.assignment.CaseAssignment;
import in.devmedi.kiosk.module.physician.assignment.CaseAssignmentRepository;
import in.devmedi.kiosk.module.physician.assignment.AssignmentStatus;
import in.devmedi.kiosk.module.physician.entity.CompletedCaseEntity;
import in.devmedi.kiosk.module.physician.entity.CompletedCaseAnswerEntity;
import in.devmedi.kiosk.module.physician.repository.CompletedCaseAnswerRepository;
import in.devmedi.kiosk.module.physician.repository.CompletedCaseRepository;
import in.devmedi.kiosk.module.physician.review.PhysicianReviewEntry;
import in.devmedi.kiosk.module.physician.review.PhysicianReviewEntryRepository;
import in.devmedi.kiosk.module.physician.service.PhysicianTimelineService;
import in.devmedi.kiosk.module.voice.language.LanguageService;
import in.devmedi.kiosk.module.voice.language.SupportedLanguage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Assembles the physician dashboard and the per-case workspace from persisted
 * data. No clinical data is invented; every output is deterministic and
 * repeatable from the database state alone.
 */
@Service
public class PhysicianCaseWorkspaceService {

    private static final String LOCAL_IDENTITY_NOTE =
            "This device is running in demo mode. The patient identity is local and not "
                    + "linked to an ABHA (Ayushman Bharat Health Account).";

    private final CompletedCaseRepository completedCaseRepository;
    private final CompletedCaseAnswerRepository answerRepository;
    private final PhysicianReviewEntryRepository reviewRepository;
    private final ClinicalDocumentRepository documentRepository;
    private final ConsentRepository consentRepository;
    private final AuditEventRepository auditEventRepository;
    private final RedFlagEvaluator redFlagEvaluator;
    private final LanguageService languageService;
    private final PhysicianTimelineService timelineService;
    private final HisIntegrationBoundary hisIntegrationBoundary;
    private final CaseAssignmentRepository assignmentRepository;

    public PhysicianCaseWorkspaceService(CompletedCaseRepository completedCaseRepository,
                                         CompletedCaseAnswerRepository answerRepository,
                                         PhysicianReviewEntryRepository reviewRepository,
                                         ClinicalDocumentRepository documentRepository,
                                         ConsentRepository consentRepository,
                                         AuditEventRepository auditEventRepository,
                                         RedFlagEvaluator redFlagEvaluator,
                                         LanguageService languageService,
                                         PhysicianTimelineService timelineService,
                                         HisIntegrationBoundary hisIntegrationBoundary,
                                         CaseAssignmentRepository assignmentRepository) {
        this.completedCaseRepository = completedCaseRepository;
        this.answerRepository = answerRepository;
        this.reviewRepository = reviewRepository;
        this.documentRepository = documentRepository;
        this.consentRepository = consentRepository;
        this.auditEventRepository = auditEventRepository;
        this.redFlagEvaluator = redFlagEvaluator;
        this.languageService = languageService;
        this.timelineService = timelineService;
        this.hisIntegrationBoundary = hisIntegrationBoundary;
        this.assignmentRepository = assignmentRepository;
    }

    // ─── Dashboard ────────────────────────────────────────────────────

    /**
     * Builds all dashboard rows, newest first, annotated with queue state
     * (unassigned pool / assigned-to-whom / assigned-to-the-viewer).
     */
    @Transactional(readOnly = true)
    public List<CaseListItem> dashboardCases(Long viewerPhysicianId) {
        List<CompletedCaseEntity> cases = completedCaseRepository.findAllByOrderByCreatedAtDesc();
        Map<String, Long> assigneeIds = new HashMap<>();
        Map<String, String> assigneeNames = new HashMap<>();
        for (CaseAssignment assignment : assignmentRepository.findByStatusOrderByAssignedAtDesc(AssignmentStatus.ACTIVE)) {
            assigneeIds.put(assignment.getCompletedCase().getCaseId(), assignment.getPhysician().getId());
            assigneeNames.put(assignment.getCompletedCase().getCaseId(), assignment.getPhysician().getUsername());
        }
        List<CaseListItem> items = new ArrayList<>(cases.size());
        for (CompletedCaseEntity c : cases) {
            String caseId = c.getCaseId();
            List<CompletedCaseAnswerEntity> answers = answerRepository.findByCaseIdOrderByAnswerOrder(caseId);
            List<PhysicianReviewEntry> reviews = reviewRepository.findByCompletedCase_CaseIdOrderByAnswerOrder(caseId);
            long documentCount = documentRepository.countByCompletedCase_CaseId(caseId);
            Set<String> langs = new LinkedHashSet<>();
            int flaggedCount = 0;
            for (CompletedCaseAnswerEntity a : answers) {
                langs.add(a.getAnswerLanguage());
                if (!redFlagEvaluator.evaluate(a.getAnswer()).isEmpty()) {
                    flaggedCount++;
                }
            }
            String patientLabel = c.getUser() != null ? c.getUser().getUsername() : "Anonymous";
            boolean assigned = assigneeIds.containsKey(caseId);
            boolean assignedToMe = assigned && assigneeIds.get(caseId).equals(viewerPhysicianId);
            items.add(new CaseListItem(
                    caseId,
                    c.getCreatedAt(),
                    patientLabel,
                    c.getUser() != null,
                    LOCAL_IDENTITY_NOTE,
                    answers.size(),
                    flaggedCount,
                    (int) documentCount,
                    reviews.size(),
                    !reviews.isEmpty() && reviews.size() == answers.size() && !answers.isEmpty(),
                    langs.stream().toList(),
                    assigned,
                    assigned ? assigneeNames.get(caseId) : "",
                    assignedToMe));
        }
        return List.copyOf(items);
    }

    // ─── Case workspace ───────────────────────────────────────────────

    /**
     * Assembles the complete case workspace view, or empty when the case
     * does not exist. Annotates the queue state relative to the requesting
     * physician; the caller is responsible for {@code requireAccess} so a
     * locked case never reaches this assembly.
     */
    @Transactional(readOnly = true)
    public Optional<WorkspaceView> workspace(String caseId, Long viewerPhysicianId) {
        CompletedCaseEntity caseEntity = completedCaseRepository.findByCaseId(caseId).orElse(null);
        if (caseEntity == null) {
            return Optional.empty();
        }

        List<CompletedCaseAnswerEntity> answers =
                answerRepository.findByCaseIdOrderByAnswerOrder(caseId);
        Map<Integer, PhysicianReviewEntry> reviewMap = reviewRepository
                .findByCompletedCase_CaseIdOrderByAnswerOrder(caseId).stream()
                .collect(java.util.stream.Collectors.toMap(
                        PhysicianReviewEntry::getAnswerOrder, r -> r));

        WorkspaceIdentity identity = caseEntity.getUser() != null
                ? WorkspaceIdentity.linked(caseEntity.getUser().getUsername())
                : WorkspaceIdentity.unlinked();

        List<WorkspaceSection> sections = buildSections(answers, reviewMap);

        int totalFlags = 0;
        RedFlagSeverity maxSeverity = RedFlagSeverity.NONE;
        for (CompletedCaseAnswerEntity a : answers) {
            List<RedFlag> flags = redFlagEvaluator.evaluate(a.getAnswer());
            totalFlags += flags.size();
            RedFlagSeverity s = redFlagEvaluator.overallSeverity(flags);
            if (s == RedFlagSeverity.URGENT) {
                maxSeverity = RedFlagSeverity.URGENT;
            }
        }

        List<DocumentWorkspaceItem> documents = timelineService.workspaceItems(caseId);

        List<ConsentWorkspaceItem> consents = consentsFor(caseEntity);
        InteropBoundaryView interop = interopBoundary();

        List<AuditWorkspaceItem> auditEvents = auditEventRepository
                .findByCaseIdOrderByOccurredAtDesc(caseId).stream()
                .map(e -> new AuditWorkspaceItem(
                        e.getOccurredAt(), e.getEventType().name(), e.getOperation(),
                        e.getOutcome().name(), e.getFailureReason()))
                .toList();

        Optional<CaseAssignment> activeAssignment = assignmentRepository
                .findByCompletedCase_CaseIdAndStatus(caseId, AssignmentStatus.ACTIVE).stream()
                .findFirst();

        return Optional.of(new WorkspaceView(
                caseId,
                caseEntity.getCreatedAt(),
                identity,
                sections,
                answers.size(),
                totalFlags,
                maxSeverity == RedFlagSeverity.URGENT ? "Urgent" : "None",
                documents,
                consents,
                interop,
                auditEvents,
                "/physician/cases/" + caseId + "/timeline",
                "/physician/cases/" + caseId + "/fhir",
                activeAssignment.isPresent(),
                activeAssignment.map(a -> a.getPhysician().getUsername()).orElse(""),
                activeAssignment.isPresent()
                        && activeAssignment.get().getPhysician().getId().equals(viewerPhysicianId)));
    }

    // ─── Internals ────────────────────────────────────────────────────

    private List<WorkspaceSection> buildSections(List<CompletedCaseAnswerEntity> answers,
                                                  Map<Integer, PhysicianReviewEntry> reviewMap) {
        Map<String, List<AnswerEntryView>> grouped = new LinkedHashMap<>();
        for (CompletedCaseAnswerEntity a : answers) {
            ClinicalAnswer ca = a.toClinicalAnswer();
            List<RedFlag> flags = redFlagEvaluator.evaluate(ca.answer());

            QuestionSource qSrc = QuestionSource.valueOf(a.getQuestionSource());
            AnswerSource aSrc = AnswerSource.valueOf(a.getAnswerSource());
            SupportedLanguage lang = languageService.find(a.getAnswerLanguage()).orElse(null);

            ReviewView review = reviewMap.containsKey(a.getAnswerOrder())
                    ? reviewView(reviewMap.get(a.getAnswerOrder()))
                    : ReviewView.none();

            AnswerEntryView entry = new AnswerEntryView(
                    a.getAnswerOrder(),
                    a.getQuestionId(),
                    a.getSection(),
                    a.getQuestionType(),
                    a.getQuestionText(),
                    a.getDisplayedQuestionText(),
                    qSrc.name(),
                    questionSourceLabel(qSrc),
                    aSrc.name(),
                    answerSourceLabel(aSrc),
                    a.getAnswerLanguage(),
                    lang != null ? lang.nativeName() : a.getAnswerLanguage(),
                    a.getAnswer(),
                    flags.stream()
                            .map(f -> new DerivedFlagView(f.id(), f.severity().name(), f.title(), f.message()))
                            .toList(),
                    review);

            String label = sectionLabel(a.getSection());
            grouped.computeIfAbsent(label, k -> new ArrayList<>()).add(entry);
        }
        List<WorkspaceSection> sections = new ArrayList<>();
        for (Map.Entry<String, List<AnswerEntryView>> e : grouped.entrySet()) {
            sections.add(new WorkspaceSection(e.getKey(), e.getKey(), e.getValue()));
        }
        return sections;
    }

    private ReviewView reviewView(PhysicianReviewEntry entry) {
        return new ReviewView(true,
                entry.getDecision().name(),
                entry.getAmendedText(),
                entry.getRationale(),
                entry.getReviewerUsername(),
                entry.getDecidedAt());
    }

    private List<ConsentWorkspaceItem> consentsFor(CompletedCaseEntity caseEntity) {
        if (caseEntity.getUser() == null) {
            return List.of();
        }
        return consentRepository.findByUserId(caseEntity.getUser().getId()).stream()
                .map(c -> new ConsentWorkspaceItem(
                        c.getConsentType().name(),
                        c.getConsentType().readableName(),
                        c.getState().name(),
                        c.getPurpose(),
                        c.getGrantedAt(),
                        c.getRevokedAt()))
                .toList();
    }

    private InteropBoundaryView interopBoundary() {
        return new InteropBoundaryView(
                hisIntegrationBoundary.isConfigured(),
                hisIntegrationBoundary.isConfigured(),
                hisIntegrationBoundary.transportLabel(),
                hisIntegrationBoundary.boundaryNote());
    }

    // ─── Labels ───────────────────────────────────────────────────────

    private static String questionSourceLabel(QuestionSource source) {
        return switch (source) {
            case DETERMINISTIC -> "Deterministic wording";
            case TRANSLATED -> "Translated wording (offline curated)";
            case AI_GENERATED -> "AI-reworded (English)";
        };
    }

    private static String answerSourceLabel(AnswerSource source) {
        return switch (source) {
            case TEXT -> "Typed";
            case VOICE -> "Voice-transcribed";
        };
    }

    private static String sectionLabel(String code) {
        return switch (code) {
            case "CHIEF_COMPLAINT", "HISTORY_OF_PRESENT_ILLNESS" ->
                    "History of Present Illness / SOCRATES";
            case "DASHAVIDHA" -> "Dashavidha Pariksha";
            case "AHARA_VIHARA" -> "Ahara-Vihara (Diet, Lifestyle & Routine)";
            default -> code;
        };
    }
}