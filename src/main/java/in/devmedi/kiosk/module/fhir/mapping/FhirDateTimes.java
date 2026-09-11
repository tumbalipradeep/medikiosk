package in.devmedi.kiosk.module.fhir.mapping;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * FHIR date/time formatting helpers.
 *
 * <p>FHIR {@code dateTime}/{@code instant} values are rendered as UTC ISO 8601
 * timestamps of second precision (e.g. {@code 2026-09-11T05:25:00Z}), the
 * narrowest truthful precision supported by the persisted {@link Instant}
 * source values.</p>
 */
public final class FhirDateTimes {

    private static final DateTimeFormatter UTC_INSTANT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX").withZone(ZoneOffset.UTC);

    private FhirDateTimes() {
    }

    /** Formats an {@link Instant} as a FHIR instant, or {@code null} for a null input. */
    public static String instant(Instant instant) {
        if (instant == null) {
            return null;
        }
        return UTC_INSTANT.format(instant.truncatedTo(ChronoUnit.SECONDS));
    }
}