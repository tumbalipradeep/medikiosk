package in.devmedi.kiosk.module.physician.assignment;

import in.devmedi.kiosk.module.auth.entity.Role;
import in.devmedi.kiosk.module.auth.entity.User;
import in.devmedi.kiosk.module.auth.repository.UserRepository;
import in.devmedi.kiosk.module.physician.entity.CompletedCaseEntity;
import in.devmedi.kiosk.module.physician.repository.CompletedCaseRepository;
import in.devmedi.kiosk.module.physician.workspace.CaseNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Owns the physician case queue. A case with no active assignment sits in the
 * shared pool and any physician may open it; a physician claims it with
 * {@link #assign(String, Long)}, which makes it private to them until they
 * release it {@link #unassign(String, Long)} or complete it
 * {@link #complete(String, Long)}.
 *
 * <p>Access control is enforced by {@link #requireAccess(String, Long)} on
 * every physician case surface, so a physician can never silently open a case
 * that another physician is actively working.</p>
 */
@Service
public class CaseAssignmentService {

    private final CaseAssignmentRepository assignmentRepository;
    private final CompletedCaseRepository caseRepository;
    private final UserRepository userRepository;

    public CaseAssignmentService(CaseAssignmentRepository assignmentRepository,
                                 CompletedCaseRepository caseRepository,
                                 UserRepository userRepository) {
        this.assignmentRepository = assignmentRepository;
        this.caseRepository = caseRepository;
        this.userRepository = userRepository;
    }

    /**
     * Assigns the case to the given physician, releasing any other active
     * assignment. Idempotent: assigning to the same physician again is a no-op.
     */
    @Transactional
    public void assign(String caseId, Long physicianId) {
        CompletedCaseEntity completedCase = requireCase(caseId);
        User physician = requirePhysician(physicianId);
        List<CaseAssignment> active = assignmentRepository
                .findByCompletedCase_CaseIdAndStatus(caseId, AssignmentStatus.ACTIVE);
        for (CaseAssignment assignment : active) {
            if (assignment.getPhysician().getId().equals(physicianId)) {
                return;
            }
            assignment.unassign();
            assignmentRepository.save(assignment);
        }
        assignmentRepository.save(new CaseAssignment(completedCase, physician, physician));
    }

    /**
     * Releases the calling physician's active assignment back to the pool.
     */
    @Transactional
    public void unassign(String caseId, Long physicianId) {
        requireCase(caseId);
        assignmentRepository.findByCompletedCase_CaseIdAndPhysicianId(caseId, physicianId)
                .filter(a -> a.getStatus() == AssignmentStatus.ACTIVE)
                .ifPresent(a -> {
                    a.unassign();
                    assignmentRepository.save(a);
                });
    }

    /**
     * Marks the calling physician's assignment COMPLETED (e.g. when the
     * clinical record is finalized).
     */
    @Transactional
    public void complete(String caseId, Long physicianId) {
        requireCase(caseId);
        assignmentRepository.findByCompletedCase_CaseIdAndPhysicianId(caseId, physicianId)
                .filter(a -> a.getStatus() == AssignmentStatus.ACTIVE
                        || a.getStatus() == AssignmentStatus.UNASSIGNED)
                .ifPresent(a -> {
                    a.complete();
                    assignmentRepository.save(a);
                });
    }

    /**
     * @return true when the physician can open the case: it exists and is
     *         either unassigned (pool) or actively assigned to them
     */
    @Transactional(readOnly = true)
    public boolean canAccess(String caseId, Long physicianId) {
        if (physicianId == null || caseId == null) {
            return false;
        }
        return caseRepository.findByCaseId(caseId)
                .map(ignored -> activeAssigneeId(caseId)
                        .map(assigneeId -> assigneeId.equals(physicianId))
                        .orElse(true))
                .orElse(false);
    }

    /**
     * Gateway guard for physician case surfaces: 404 for a missing case, 403
     * when the case is locked to another physician's active assignment.
     */
    @Transactional(readOnly = true)
    public void requireAccess(String caseId, Long physicianId) {
        if (caseId == null || caseRepository.findByCaseId(caseId).isEmpty()) {
            throw new CaseNotFoundException(caseId == null ? "" : caseId);
        }
        Optional<Long> assigneeId = activeAssigneeId(caseId);
        if (assigneeId.isPresent() && !assigneeId.get().equals(physicianId)) {
            throw new CaseAccessDeniedException(caseId);
        }
    }

    /**
     * @return the username of the physician currently holding the case, when
     *         it has an active assignment
     */
    @Transactional(readOnly = true)
    public Optional<String> activeAssigneeUsername(String caseId) {
        return assignmentRepository.findByCompletedCase_CaseIdAndStatus(caseId, AssignmentStatus.ACTIVE)
                .stream()
                .findFirst()
                .map(a -> a.getPhysician().getUsername());
    }

    /**
     * @return the id of the physician currently holding the case, when it has
     *         an active assignment
     */
    @Transactional(readOnly = true)
    public Optional<Long> activeAssigneeId(String caseId) {
        return assignmentRepository.findByCompletedCase_CaseIdAndStatus(caseId, AssignmentStatus.ACTIVE)
                .stream()
                .findFirst()
                .map(a -> a.getPhysician().getId());
    }

    private CompletedCaseEntity requireCase(String caseId) {
        return caseRepository.findByCaseId(caseId)
                .orElseThrow(() -> new CaseNotFoundException(caseId));
    }

    private User requirePhysician(Long physicianId) {
        User physician = userRepository.findById(physicianId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown physician user id: " + physicianId));
        if (physician.getRole() != Role.PHYSICIAN) {
            throw new IllegalArgumentException("User is not a physician: " + physician.getUsername());
        }
        return physician;
    }
}