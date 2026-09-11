package in.devmedi.kiosk.module.fhir.mapping;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class FhirDateTimesTest {

    @Test
    void formatsInstantAsUtcIso8601SecondPrecision() {
        Instant instant = Instant.parse("2026-01-02T03:04:05Z");
        assertThat(FhirDateTimes.instant(instant)).isEqualTo("2026-01-02T03:04:05Z");
    }

    @Test
    void truncatesSubSecondPrecision() {
        Instant instant = Instant.parse("2026-01-02T03:04:05.987654321Z");
        assertThat(FhirDateTimes.instant(instant)).isEqualTo("2026-01-02T03:04:05Z");
    }

    @Test
    void normalizesToUtc() {
        Instant instant = Instant.parse("2026-01-02T03:04:05Z");
        assertThat(FhirDateTimes.instant(instant)).isEqualTo("2026-01-02T03:04:05Z");
    }

    @Test
    void nullInstantIsNull() {
        assertThat(FhirDateTimes.instant(null)).isNull();
    }
}