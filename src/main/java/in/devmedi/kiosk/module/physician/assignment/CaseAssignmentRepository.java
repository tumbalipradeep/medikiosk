package in.devmedi.kiosk.module.physician.assignment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CaseAssignmentRepository extends JpaRepository<CaseAssignment, Long> {

    Optional<CaseAssignment> findByCompletedCase_CaseIdAndPhysicianId(String caseId, Long physicianId);

    List<CaseAssignment> findByCompletedCase_CaseIdOrderByAssignedAtAsc(String caseId);

    List<CaseAssignment> findByPhysicianIdAndStatusOrderByAssignedAtDesc(
            Long physicianId, AssignmentStatus status);

    List<CaseAssignment> findByCompletedCase_CaseIdAndStatus(String caseId, AssignmentStatus status);

    List<CaseAssignment> findByStatusOrderByAssignedAtDesc(AssignmentStatus status);

    boolean existsByCompletedCase_CaseIdAndStatus(String caseId, AssignmentStatus status);
}