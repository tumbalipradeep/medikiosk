package in.devmedi.kiosk.module.clinical.summary;

import java.util.List;

/**
 * One section of a clinical summary, in deterministic order.
 *
 * <p>Sections are populated from captured answers of the corresponding
 * conversation section; the original answering order is preserved.</p>
 *
 * @param name    section display name (e.g. History of Present Illness / SOCRATES)
 * @param entries the section's summary entries, in original answering order
 */
public record ClinicalSummarySection(String name, List<ClinicalSummaryEntry> entries) {

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}