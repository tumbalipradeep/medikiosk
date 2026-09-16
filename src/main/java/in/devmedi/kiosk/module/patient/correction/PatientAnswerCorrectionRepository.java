package in.devmedi.kiosk.module.patient.correction;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Persistence for patient answer corrections, keyed by case and answer order. */
public interface PatientAnswerCorrectionRepository extends JpaRepository<PatientAnswerCorrection, Long> {

    List<PatientAnswerCorrection> findByCompletedCase_CaseIdOrderByAnswerOrder(String caseId);
}
