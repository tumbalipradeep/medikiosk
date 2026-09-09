package in.devmedi.kiosk.module.clinical.summary;

import java.util.List;

/**
 * Structured, deterministic clinical summary of a completed (or partial)
 * patient intake conversation.
 *
 * <p>Holds the captured answers organized by the existing clinical sections
 * in a fixed order. It is a pure in-memory value object: nothing is persisted,
 * scored, or interpreted.</p>
 *
 * @param sections      summary sections in canonical order
 * @param answeredCount total number of captured answers
 */
public record ClinicalSummary(List<ClinicalSummarySection> sections, int answeredCount) {

    public int entryCount() {
        return sections.stream().mapToInt(section -> section.entries().size()).sum();
    }
}