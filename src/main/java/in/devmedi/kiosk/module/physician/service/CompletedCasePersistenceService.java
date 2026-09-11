package in.devmedi.kiosk.module.physician.service;

import in.devmedi.kiosk.module.auth.entity.User;
import in.devmedi.kiosk.module.auth.repository.UserRepository;
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
    private final UserRepository userRepository;

    public CompletedCasePersistenceService(CompletedCaseRepository completedCaseRepository,
                                           CompletedCaseAnswerRepository answerRepository,
                                           UserRepository userRepository) {
        this.completedCaseRepository = completedCaseRepository;
        this.answerRepository = answerRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void save(CompletedCase completed) {
        save(completed, null);
    }

    @Transactional
    public void save(CompletedCase completed, Long userId) {
        CompletedCaseEntity caseEntity = completedCaseRepository.findByCaseId(completed.id())
                .orElseGet(() -> new CompletedCaseEntity(completed.id()));

        if (userId != null && caseEntity.getUser() == null) {
            userRepository.findById(userId).ifPresent(caseEntity::setUser);
        }

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

    @Transactional(readOnly = true)
    public Optional<CompletedCase> findLatest() {
        return completedCaseRepository.findTopByOrderByCreatedAtDesc()
                .flatMap(entity -> loadByCaseId(entity.getCaseId()));
    }

    @Transactional(readOnly = true)
    public Optional<CompletedCase> findByCaseId(String caseId) {
        return loadByCaseId(caseId);
    }

    @Transactional(readOnly = true)
    public Optional<CompletedCase> findLatestByUser(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return completedCaseRepository.findTopByUserIdOrderByCreatedAtDesc(userId)
                .flatMap(entity -> loadByCaseId(entity.getCaseId()));
    }

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
            Long userId = entity.getUser() != null ? entity.getUser().getId() : null;
            return new CompletedCase(entity.getCaseId(), result, userId);
        });
    }
}