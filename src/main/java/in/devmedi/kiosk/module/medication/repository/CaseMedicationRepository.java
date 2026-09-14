package in.devmedi.kiosk.module.medication.repository;

import in.devmedi.kiosk.module.medication.entity.CaseMedication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CaseMedicationRepository extends JpaRepository<CaseMedication, Long> {

    List<CaseMedication> findByCaseIdOrderByMedNameAsc(String caseId);

    Optional<CaseMedication> findByCaseIdAndMedNameIgnoreCase(String caseId, String medName);
}