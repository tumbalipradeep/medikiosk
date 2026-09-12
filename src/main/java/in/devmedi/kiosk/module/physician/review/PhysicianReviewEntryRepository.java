package in.devmedi.kiosk.module.physician.review;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Persistence for physician review decisions, keyed by stable
 * {@code (case_id, answer_order)}.
 */
public interface PhysicianReviewEntryRepository extends JpaRepository<PhysicianReviewEntry, Long> {

    Optional<PhysicianReviewEntry> findByCompletedCase_CaseIdAndAnswerOrder(String caseId, int answerOrder);

    List<PhysicianReviewEntry> findByCompletedCase_CaseIdOrderByAnswerOrder(String caseId);
}