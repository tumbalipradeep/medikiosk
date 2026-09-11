package in.devmedi.kiosk.module.document.findings.model;

/**
 * One decoded vital sign.
 *
 * @param type            which vital was recognised
 * @param value           normalised numeric text (e.g. {@code 128/82})
 * @param unit            normalised unit, or {@code null} when the source did
 *                        not state a unit (units are never invented)
 * @param sourceSnippet   the full document line the value was read from
 * @param occurrenceIndex zero-based position among vitals in document order
 */
public record VitalSign(VitalType type,
                        String value,
                        String unit,
                        String sourceSnippet,
                        int occurrenceIndex) {
}