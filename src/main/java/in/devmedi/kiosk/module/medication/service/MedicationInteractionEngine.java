package in.devmedi.kiosk.module.medication.service;

import in.devmedi.kiosk.module.medication.entity.MedicationInteractionRule;
import in.devmedi.kiosk.module.medication.enums.MedicationInteractionSeverity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Matches the detected medicines of a case against the seeded interaction
 * rule set. The engine is deliberately dumb and data-driven: a pair triggers
 * when each medicine matches one side of a rule by canonical drug name or by
 * therapeutic class key, and matching is symmetric, so no ordering logic or
 * drug-pair knowledge lives in code.
 *
 * <p>Identical rule hits between different medicine rows are collapsed to one
 * screening row (the guidance is per class pair), and results are ordered
 * deterministic: most severe first, then by title and drug names, so repeated
 * page loads and test runs are stable.</p>
 */
@Component
public class MedicationInteractionEngine {

    /**
     * @param meds  detected medicines of the case (already deduplicated)
     * @param rules the seeded interaction rules
     * @return screened interactions, most severe first
     */
    public List<MedicationInteractionView> analyze(List<DetectedMedication> meds,
                                                   List<MedicationInteractionRule> rules) {
        List<MedicationInteractionView> results = new ArrayList<>();
        if (meds == null || rules == null || meds.size() < 2) {
            return results;
        }
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < meds.size(); i++) {
            for (int j = i + 1; j < meds.size(); j++) {
                MedicationInteractionRule rule = findRule(rules, meds.get(i), meds.get(j));
                if (rule == null) {
                    continue;
                }
                String key = rule.getSeverity().name() + "|" + rule.getTitle();
                if (!seen.add(key)) {
                    continue;
                }
                results.add(new MedicationInteractionView(
                        meds.get(i).displayName(),
                        meds.get(j).displayName(),
                        rule.getSeverity(),
                        rule.getTitle(),
                        rule.getDescription(),
                        rule.getGuidance()));
            }
        }
        results.sort(Comparator
                .comparing(MedicationInteractionView::severity, Comparator.reverseOrder())
                .thenComparing(MedicationInteractionView::title)
                .thenComparing(MedicationInteractionView::drugA)
                .thenComparing(MedicationInteractionView::drugB));
        return results;
    }

    /**
     * @return the overall severity across a list of screened interactions, or
     *         {@code null} when the list is empty
     */
    public static MedicationInteractionSeverity overall(List<MedicationInteractionView> interactions) {
        MedicationInteractionSeverity overall = null;
        if (interactions != null) {
            for (MedicationInteractionView view : interactions) {
                overall = MedicationInteractionSeverity.highest(overall, view.severity());
            }
        }
        return overall;
    }

    private MedicationInteractionRule findRule(List<MedicationInteractionRule> rules,
                                               DetectedMedication a,
                                               DetectedMedication b) {
        for (MedicationInteractionRule rule : rules) {
            boolean forward = matches(rule.getGroupA(), a) && matches(rule.getGroupB(), b);
            boolean reverse = matches(rule.getGroupA(), b) && matches(rule.getGroupB(), a);
            if (forward || reverse) {
                return rule;
            }
        }
        return null;
    }

    private boolean matches(String group, DetectedMedication med) {
        if (group == null || med == null) {
            return false;
        }
        if (med.classKey() != null && group.equalsIgnoreCase(med.classKey())) {
            return true;
        }
        return med.canonicalName() != null && group.equalsIgnoreCase(med.canonicalName());
    }
}