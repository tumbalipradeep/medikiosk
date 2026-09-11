package in.devmedi.kiosk.module.document.findings.model;

import java.time.Instant;

/**
 * One deterministic event in the clinical timeline. Every field is derived from
 * persisted data only; nothing is fabricated or inferred.
 *
 * @param eventType           event classification
 * @param eventDate           the explicit clinical date (dd/MM/yyyy) when one
 *                            existed in the source, or {@code null} when no date
 *                            was present
 * @param label               concise human-readable label for the event
 * @param details             value/details string, or {@code null}
 * @param sourceDocumentId    documentId of the originating clinical document
 * @param sourceDocumentFilename original filename of the originating document
 * @param sourceSnippet       verbatim excerpt from the source, or {@code null}
 * @param sortInstant         instant used for chronological sorting (upload
 *                            timestamp when no clinical date exists)
 * @param occurrenceIndex     stable sub-ordering for same-date events
 */
public record TimelineEvent(TimelineEventType eventType,
                            String eventDate,
                            String label,
                            String details,
                            String sourceDocumentId,
                            String sourceDocumentFilename,
                            String sourceSnippet,
                            Instant sortInstant,
                            int occurrenceIndex) {
}