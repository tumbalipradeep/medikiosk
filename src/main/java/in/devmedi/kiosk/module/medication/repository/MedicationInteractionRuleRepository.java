package in.devmedi.kiosk.module.medication.repository;

import in.devmedi.kiosk.module.medication.entity.MedicationInteractionRule;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicationInteractionRuleRepository extends JpaRepository<MedicationInteractionRule, Long> {
}