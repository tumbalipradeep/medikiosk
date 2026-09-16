package in.devmedi.kiosk.module.physician.repository;

import in.devmedi.kiosk.module.physician.entity.CompletedCaseAnswerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CompletedCaseAnswerRepository extends JpaRepository<CompletedCaseAnswerEntity, Long> {

    /**
     * Loads a case's answers in answering order. The root entity is fetched
     * eagerly so the ordered answers are available outside of a session.
     */
    @Query("select a from CompletedCaseAnswerEntity a "
            + "join fetch a.completedCase where a.completedCase.caseId = :caseId "
            + "order by a.answerOrder asc")
    List<CompletedCaseAnswerEntity> findByCaseIdOrderByAnswerOrder(@Param("caseId") String caseId);

    /** Single answer lookup for the patient correction loop's snapshot verification. */
    Optional<CompletedCaseAnswerEntity> findByCompletedCase_CaseIdAndAnswerOrder(String caseId, int answerOrder);

    void deleteByCompletedCase_CaseId(String caseId);
}