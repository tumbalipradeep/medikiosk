package in.devmedi.kiosk.module.document.findings.model;

/**
 * One decoded laboratory result.
 *
 * @param testName         canonical test name from the lab-test registry
 * @param rawValue         value exactly as printed (e.g. {@code 6,500})
 * @param value            normalised numeric text (e.g. {@code 7200})
 * @param unit             normalised unit, or {@code null} when not stated
 * @param referenceRange   reference range exactly as printed (no brackets), or
 *                         {@code null} when the document gave none
 * @param specimenDate     normalised collection date, or {@code null}
 * @param sourceSnippet    the full document line the result was read from
 * @param occurrenceIndex  zero-based position among lab results in document order
 * @param abnormalityStatus deterministic LOW/NORMAL/HIGH comparison against the
 *                         document's own reference range, or {@code UNKNOWN}
 */
public record LabResult(String testName,
                        String rawValue,
                        String value,
                        String unit,
                        String referenceRange,
                        String specimenDate,
                        String sourceSnippet,
                        int occurrenceIndex,
                        AbnormalityStatus abnormalityStatus) {

    public LabResult withAbnormality(AbnormalityStatus status) {
        return new LabResult(testName, rawValue, value, unit, referenceRange, specimenDate,
                sourceSnippet, occurrenceIndex, status);
    }
}