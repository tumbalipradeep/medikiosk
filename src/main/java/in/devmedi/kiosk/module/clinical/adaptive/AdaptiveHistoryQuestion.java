package in.devmedi.kiosk.module.clinical.adaptive;

import in.devmedi.kiosk.module.clinical.history.ClinicalHistoryCategory;

/**
 * One declarative question of the adaptive complete-history conversation.
 *
 * <p>Selection is data-driven, not code-driven: the bank entry states whether
 * the question is always applicable, which earlier answers trigger it (keyword
 * match), and whether it follows a screening question and should be skipped
 * when that screening answer terminates the section negatively.</p>
 *
 * @param id                  stable question id, e.g. {@code past_medical_diabetes}
 * @param category            clinical history category the answer is stored under
 * @param conceptKey          history concept key within the category
 * @param text                canonical question text
 * @param required            must be answered before the section can complete
 * @param priority            ordering within the bank (lower = earlier)
 * @param triggers            keywords in earlier answers that make this question
 *                            applicable (empty = not keyword-dependent)
 * @param parent              id of the screening question this detailing question
 *                            follows, or {@code null}
 * @param negativeTermination whether an explicitly-negative answer to this
 *                            screening question deactivates its detailing
 *                            follow-ups
 */
public record AdaptiveHistoryQuestion(
        String id,
        ClinicalHistoryCategory category,
        String conceptKey,
        String text,
        boolean required,
        int priority,
        java.util.List<String> triggers,
        String parent,
        boolean negativeTermination) {

    public AdaptiveHistoryQuestion {
        triggers = triggers == null ? java.util.List.of() : java.util.List.copyOf(triggers);
    }

    public boolean alwaysApplicable() {
        return triggers.isEmpty() && parent == null;
    }
}