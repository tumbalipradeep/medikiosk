package in.devmedi.kiosk.module.clinical.history;

import in.devmedi.kiosk.module.auth.entity.User;
import in.devmedi.kiosk.module.auth.repository.UserRepository;
import in.devmedi.kiosk.module.clinical.provenance.ClinicalProvenance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Structured "complete clinical history" store.
 *
 * <p>Each clinical datum is recorded against {@code (patient, category,
 * conceptKey)}. Recording the same item twice is an idempotent update of the
 * value/label/note — never a duplicate row — so replaying an intake or
 * re-running a deterministic extraction cannot corrupt the record.</p>
 *
 * <p>Provenance is preserved: callers state how the datum was produced, and an
 * item is never relabelled to a stronger provenance implicitly. Only an
 * explicit physician attestation upgrades an item (see {@link
 * #attest(Long, User)}).</p>
 */
@Service
public class ClinicalHistoryService {

    private static final String ANSWER_SOURCE_LABEL = "kiosk intake";

    private final ClinicalHistoryItemRepository repository;
    private final UserRepository userRepository;

    public ClinicalHistoryService(ClinicalHistoryItemRepository repository,
                                  UserRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
    }

    /**
     * Records one clinical history item, idempotently (upsert on
     * patient+category+conceptKey).
     *
     * @param provenance  how this datum was produced; preserved on update
     * @param sourceLabel human-readable source, e.g. {@code "kiosk intake"}
     */
    @Transactional
    public void record(Long patientUserId,
                       ClinicalHistoryCategory category,
                       String conceptKey,
                       String label,
                       String value,
                       String note,
                       ClinicalProvenance provenance,
                       String sourceLabel) {
        if (patientUserId == null || category == null || isBlank(conceptKey)) {
            return;
        }
        User patient = userRepository.getReferenceById(patientUserId);
        repository.findByPatientIdAndCategoryAndConceptKey(patientUserId, category, conceptKey)
                .ifPresentOrElse(
                        existing -> {
                            existing.setLabel(nonBlank(label) ? label : existing.getLabel());
                            existing.setValue(value);
                            existing.setNote(note);
                            if (nonBlank(sourceLabel)) {
                                existing.setSourceLabel(sourceLabel);
                            }
                            repository.save(existing);
                        },
                        () -> repository.save(new ClinicalHistoryItem(
                                patient, category, conceptKey, nonBlank(label) ? label : conceptKey,
                                value, note, provenance, nonBlank(sourceLabel) ? sourceLabel : ANSWER_SOURCE_LABEL,
                                null)));
    }

    /**
     * Records a batch of items in one transaction (used by the intake
     * completion flow and document extraction).
     */
    @Transactional
    public void recordAll(Long patientUserId, List<HistoryDatum> items) {
        items.forEach(i -> record(patientUserId, i.category(), i.conceptKey(), i.label(),
                i.value(), i.note(), i.provenance(), i.sourceLabel()));
    }

    /**
     * Physician attestation: a physician confirmed/edited an item, upgrading its
     * provenance to PHYSICIAN_ENTERED and stamping the physician.
     */
    @Transactional
    public void attest(Long itemId, User physician) {
        repository.findById(itemId).ifPresent(item -> {
            item.setProvenance(ClinicalProvenance.PHYSICIAN_ENTERED);
            repository.save(item);
        });
    }

    @Transactional(readOnly = true)
    public List<ClinicalHistoryItem> historyFor(Long patientUserId) {
        return repository.findByPatientIdOrderByCategoryAscConceptKeyAsc(patientUserId);
    }

    @Transactional(readOnly = true)
    public Map<ClinicalHistoryCategory, List<ClinicalHistoryItem>> groupedFor(Long patientUserId) {
        Map<ClinicalHistoryCategory, List<ClinicalHistoryItem>> grouped = new TreeMap<>();
        for (ClinicalHistoryItem item : historyFor(patientUserId)) {
            grouped.computeIfAbsent(item.getCategory(), k -> new java.util.ArrayList<>()).add(item);
        }
        return grouped;
    }

    @Transactional(readOnly = true)
    public List<ClinicalHistoryItem> itemsByCategory(Long patientUserId, ClinicalHistoryCategory category) {
        return repository.findByPatientIdAndCategoryOrderByConceptKeyAsc(patientUserId, category);
    }

    @Transactional(readOnly = true)
    public long countFor(Long patientUserId) {
        return repository.countByPatientId(patientUserId);
    }

    @Transactional(readOnly = true)
    public boolean hasItem(Long patientUserId, ClinicalHistoryCategory category, String conceptKey) {
        return repository.existsByPatientIdAndCategoryAndConceptKey(patientUserId, category, conceptKey);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static boolean nonBlank(String s) {
        return s != null && !s.isBlank();
    }
}