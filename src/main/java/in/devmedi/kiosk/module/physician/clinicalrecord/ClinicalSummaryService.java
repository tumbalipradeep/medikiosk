package in.devmedi.kiosk.module.physician.clinicalrecord;

import in.devmedi.kiosk.module.auth.entity.User;
import in.devmedi.kiosk.module.auth.repository.UserRepository;
import in.devmedi.kiosk.module.clinical.provenance.ClinicalProvenance;
import in.devmedi.kiosk.module.clinical.summary.ClinicalSummaryBuilder;
import in.devmedi.kiosk.module.physician.entity.CompletedCaseEntity;
import in.devmedi.kiosk.module.physician.repository.CompletedCaseRepository;
import in.devmedi.kiosk.module.physician.service.CompletedCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Manages the physician-facing, editable clinical summary sections persisted
 * per case.
 *
 * <p>On case completion this service seeds draft sections from the deterministic
 * {@link ClinicalSummaryBuilder} with provenance {@link
 * ClinicalProvenance#SYSTEM_GENERATED}. A physician must review, optionally
 * amend, and explicitly accept each section — so AI/system output can never
 * silently become the physician-authored record.</p>
 */
@Service
public class ClinicalSummaryService {

    private static final Logger log = LoggerFactory.getLogger(ClinicalSummaryService.class);

    /** Stable short storage codes within the 24-char column, with display labels. */
    private static final Map<String, String> SECTION_CODE_BY_NAME = new LinkedHashMap<>();
    private static final Map<String, String> SECTION_LABEL_BY_CODE = new LinkedHashMap<>();

    static {
        registerSection("History of Present Illness / SOCRATES", "HPI_SOCRATES", "History of Present Illness (SOCRATES)");
        registerSection("Dashavidha Pariksha", "DASHAVIDHA", "Dashavidha Pariksha");
        registerSection("Ahara-Vihara", "AHARA_VIHARA", "Ahara-Vihara");
    }

    private static void registerSection(String summaryName, String code, String label) {
        SECTION_CODE_BY_NAME.put(summaryName, code);
        SECTION_LABEL_BY_CODE.put(code, label);
    }

    private final ClinicalSummaryRepository repository;
    private final CompletedCaseRepository completedCaseRepository;
    private final UserRepository userRepository;
    private final ClinicalSummaryBuilder summaryBuilder;

    public ClinicalSummaryService(ClinicalSummaryRepository repository,
                                  CompletedCaseRepository completedCaseRepository,
                                  UserRepository userRepository,
                                  ClinicalSummaryBuilder summaryBuilder) {
        this.repository = repository;
        this.completedCaseRepository = completedCaseRepository;
        this.userRepository = userRepository;
        this.summaryBuilder = summaryBuilder;
    }

    /**
     * Seeds DRAFT sections from the deterministic summary builder.
     * Idempotent: existing sections are not overwritten.
     */
    @Transactional
    public void seedDrafts(CompletedCase completedCase) {
        if (completedCase == null || completedCase.result() == null) {
            return;
        }
        CompletedCaseEntity caseEntity = completedCaseRepository.findByCaseId(completedCase.id())
                .orElseThrow(() -> new IllegalArgumentException("Unknown case: " + completedCase.id()));
        in.devmedi.kiosk.module.clinical.summary.ClinicalSummary summary =
                summaryBuilder.summarize(completedCase.result());
        for (in.devmedi.kiosk.module.clinical.summary.ClinicalSummarySection section : summary.sections()) {
            if (section.isEmpty()) {
                continue;
            }
            String content = section.entries().stream()
                    .map(e -> e.questionText() + ": " + e.answer())
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse("");
            if (content.isBlank()) {
                continue;
            }
            String code = sectionCode(section.name());
            repository.findByCompletedCase_CaseIdAndSection(completedCase.id(), code)
                    .ifPresentOrElse(
                            existing -> log.debug("Clinical summary section '{}' for case '{}' already exists", code, completedCase.id()),
                            () -> repository.save(new ClinicalSummary(
                                    caseEntity,
                                    code,
                                    content,
                                    ClinicalProvenance.SYSTEM_GENERATED,
                                    null)));
        }
    }

    /** Deterministic short storage code for a summary section name (≤24 chars). */
    private static String sectionCode(String name) {
        return SECTION_CODE_BY_NAME.getOrDefault(name, name.length() <= 24 ? name : name.substring(0, 21) + "...");
    }

    /** Human-readable label for a stored section code (falls back to the code). */
    public static String sectionLabel(String code) {
        return SECTION_LABEL_BY_CODE.getOrDefault(code, code);
    }

    @Transactional(readOnly = true)
    public List<ClinicalSummary> sectionsForCase(String caseId) {
        return repository.findByCompletedCase_CaseIdOrderBySectionAsc(caseId);
    }

    @Transactional(readOnly = true)
    public Optional<ClinicalSummary> section(String caseId, String section) {
        return repository.findByCompletedCase_CaseIdAndSection(caseId, section);
    }

    @Transactional
    public void amend(String caseId, String sectionName, String newContent, Long physicianUserId) {
        ClinicalSummary section = repository.findByCompletedCase_CaseIdAndSection(caseId, sectionName)
                .orElseThrow(() -> new IllegalArgumentException("Section '" + sectionName + "' not found for case '" + caseId + "'"));
        section.amend(newContent);
        repository.save(section);
        log.info("Clinical summary amended: case='{}' section='{}' by userId={}", caseId, sectionName, physicianUserId);
    }

    @Transactional
    public void accept(String caseId, String sectionName, String reviewedContent, Long physicianUserId) {
        ClinicalSummary section = repository.findByCompletedCase_CaseIdAndSection(caseId, sectionName)
                .orElseThrow(() -> new IllegalArgumentException("Section '" + sectionName + "' not found for case '" + caseId + "'"));
        section.accept(reviewedContent);
        repository.save(section);
        log.info("Clinical summary accepted: case='{}' section='{}' by userId={}", caseId, sectionName, physicianUserId);
    }

    @Transactional
    public void reject(String caseId, String sectionName, Long physicianUserId) {
        ClinicalSummary section = repository.findByCompletedCase_CaseIdAndSection(caseId, sectionName)
                .orElseThrow(() -> new IllegalArgumentException("Section '" + sectionName + "' not found for case '" + caseId + "'"));
        section.reject();
        repository.save(section);
        log.info("Clinical summary rejected: case='{}' section='{}' by userId={}", caseId, sectionName, physicianUserId);
    }
}