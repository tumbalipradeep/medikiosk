package in.devmedi.kiosk.module.document.findings;

import in.devmedi.kiosk.module.document.findings.model.TimelineEvent;

import java.util.List;

/**
 * Physician-facing clinical timeline for one completed case. All events are
 * derived deterministically from persisted document, extraction, and findings
 * data. No dates are invented; events without a clinical date appear at the end
 * in stable document/occurrence order.
 *
 * @param events chronological timeline events (never null)
 */
public record TimelineResponse(List<TimelineEvent> events) {
    public static TimelineResponse empty() {
        return new TimelineResponse(List.of());
    }
}