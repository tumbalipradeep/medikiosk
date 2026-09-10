package in.devmedi.kiosk.module.physician.service;

import in.devmedi.kiosk.module.clinical.dialogue.ClinicalAnswer;
import in.devmedi.kiosk.module.clinical.dialogue.ClinicalConversationResult;
import in.devmedi.kiosk.module.physician.entity.CompletedCaseAnswerEntity;
import in.devmedi.kiosk.module.physician.entity.CompletedCaseEntity;
import in.devmedi.kiosk.module.physician.repository.CompletedCaseAnswerRepository;
import in.devmedi.kiosk.module.physician.repository.CompletedCaseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Persists and retrieves completed patient intake cases in PostgreSQL.
 *
 * <p>Each completed case is stored under its stable case identity, with every
 * captured clinical answer kept verbatim and in answering order (question id,
 * section, type, text, and raw answer). This lets the physician review flow
 * reload a case even after the in-memory review store has been cleared or the
 * application has restarted. The stable case id is unique across persisted
 * cases.</p>
 */
@Service
public class CompletedCasePersistenceService {

    private final CompletedCaseRepository completedCaseRepository;
    private final CompletedCaseAnswerRepository answerRepository;

    public CompletedCasePersistenceService(CompletedCaseRepository completedCaseRepository,
                                           CompletedCaseAnswerRepository answerRepository) {
        this.completedCaseRepository = completedCaseRepository;
        this.answerRepository = answerRepository;
    }

    /**
     * Persists a completed case under its stable identity, along with all of
     * its captured answers in answering order. If the case id already exists it
     * is replaced, keeping the id stable and the answers current.
     *
     * @param completed the completed case to persist
     */
    @Transactional
    public void save(CompletedCase completed) {
        CompletedCaseEntity caseEntity = completedCaseRepository.findByCaseId(completed.id())
                .orElseGet(() -> new CompletedCaseEntity(completed.id()));
        completedCaseRepository.saveAndFlush(caseEntity);

        List<CompletedCaseAnswerEntity> oldAnswers =
                answerRepository.findByCaseIdOrderByAnswerOrder(completed.id());
        if (!oldAnswers.isEmpty()) {
            answerRepository.deleteAll(oldAnswers);
            answerRepository.flush();
        }

        List<ClinicalAnswer> answers = completed.result().all();
        for (int i = 0; i < answers.size(); i++) {
            answerRepository.save(CompletedCaseAnswerEntity.from(caseEntity, i, answers.get(i)));
        }
    }

    /**
     * @return the most recently persisted completed case, fully reconstructed
     * in answering order, if any
     */
    @Transactional(readOnly = true)
    public Optional<CompletedCase> findLatest() {
        return completedCaseRepository.findTopByOrderByCreatedAtDesc()
                .flatMap(entity -> loadByCaseId(entity.getCaseId()));
    }

    /**
     * Loads one persisted completed case by its stable id.
     *
     * @param caseId the stable case identity
     * @return the reconstructed completed case, if present
     */
    @Transactional(readOnly = true)
    public Optional<CompletedCase> findByCaseId(String caseId) {
        return loadByCaseId(caseId);
    }

    /**
     * Deletes all persisted completed cases (used by tests and fresh starts).
     */
    @Transactional
    public void deleteAll() {
        answerRepository.deleteAllInBatch();
        completedCaseRepository.deleteAllInBatch();
    }

    private Optional<CompletedCase> loadByCaseId(String caseId) {
        return completedCaseRepository.findByCaseId(caseId).map(entity -> {
            List<CompletedCaseAnswerEntity> answerEntities =
                    answerRepository.findByCaseIdOrderByAnswerOrder(caseId);
            ClinicalConversationResult result = new ClinicalConversationResult();
            for (CompletedCaseAnswerEntity answerEntity : answerEntities) {
                result.record(answerEntity.toClinicalAnswer());
            }
            return new CompletedCase(entity.getCaseId(), result);
        });
    }
}