package in.devmedi.kiosk.module.fhir.validation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class FhirPrimitivesTest {

    @Test
    void fhirIdAcceptsRegexCharsUpTo64() {
        assertThat(FhirPrimitives.isFhirId("abc-123.A4")).isTrue();
        assertThat(FhirPrimitives.isFhirId("a")).isTrue();
        String maxLength = IntStream.range(0, 64).mapToObj(i -> "a").collect(Collectors.joining());
        assertThat(FhirPrimitives.isFhirId(maxLength)).isTrue();
    }

    @Test
    void fhirIdRejectsBlankTooLongAndInvalidChars() {
        assertThat(FhirPrimitives.isFhirId(null)).isFalse();
        assertThat(FhirPrimitives.isFhirId("")).isFalse();
        assertThat(FhirPrimitives.isFhirId(" ")).isFalse();
        assertThat(FhirPrimitives.isFhirId("a b")).isFalse();
        assertThat(FhirPrimitives.isFhirId("a/b")).isFalse();
        assertThat(FhirPrimitives.isFhirId("a#b")).isFalse();
        assertThat(FhirPrimitives.isFhirId(IntStream.range(0, 65)
                .mapToObj(i -> "a").collect(Collectors.joining()))).isFalse();
    }

    @Test
    void codeAcceptsSingleAndMultiWordTokens() {
        assertThat(FhirPrimitives.isCode("hpi_onset")).isTrue();
        assertThat(FhirPrimitives.isCode("LIFE_THREATENING_RISK_AND_PRIORITY_SCORE")).isTrue();
        assertThat(FhirPrimitives.isCode("a")).isTrue();
    }

    @Test
    void codeRejectsBlankSurroundingWhitespaceAndOverlongValues() {
        assertThat(FhirPrimitives.isCode(null)).isFalse();
        assertThat(FhirPrimitives.isCode("")).isFalse();
        assertThat(FhirPrimitives.isCode("  ")).isFalse();
        assertThat(FhirPrimitives.isCode(" code")).isFalse();
        assertThat(FhirPrimitives.isCode("code ")).isFalse();
        assertThat(FhirPrimitives.isCode(IntStream.range(0, 65)
                .mapToObj(i -> "a").collect(Collectors.joining()))).isFalse();
    }

    @Test
    void absoluteUriAcceptsSchemedValuesOnly() {
        assertThat(FhirPrimitives.isAbsoluteUri("urn:medikiosk:question-code")).isTrue();
        assertThat(FhirPrimitives.isAbsoluteUri("http://terminology.hl7.org/CodeSystem/v3-ActCode")).isTrue();
        assertThat(FhirPrimitives.isAbsoluteUri("question-code")).isFalse();
        assertThat(FhirPrimitives.isAbsoluteUri("")).isFalse();
        assertThat(FhirPrimitives.isAbsoluteUri(null)).isFalse();
    }

    @Test
    void instantAcceptsUtcAndOffsetValues() {
        assertThat(FhirPrimitives.isInstant("2026-09-11T05:25:00Z")).isTrue();
        assertThat(FhirPrimitives.isInstant("2026-09-11T05:25:00+05:30")).isTrue();
        assertThat(FhirPrimitives.isInstant("2026-09-11T05:25:00.123Z")).isTrue();
    }

    @Test
    void instantRejectsMalformedValues() {
        assertThat(FhirPrimitives.isInstant(null)).isFalse();
        assertThat(FhirPrimitives.isInstant("")).isFalse();
        assertThat(FhirPrimitives.isInstant("2026-09-11")).isFalse();
        assertThat(FhirPrimitives.isInstant("2026-09-11 05:25:00")).isFalse();
        assertThat(FhirPrimitives.isInstant("2026-09-11T05:25:00")).isFalse();
        assertThat(FhirPrimitives.isInstant("2026-09-11T25:25:00Z")).isFalse();
        assertThat(FhirPrimitives.isInstant("2026-13-11T05:25:00Z")).isFalse();
    }

    @Test
    void dateAcceptsRealCalendarDatesOnly() {
        assertThat(FhirPrimitives.isDate("2026-09-11")).isTrue();
        assertThat(FhirPrimitives.isDate("2024-02-29")).isTrue();
        assertThat(FhirPrimitives.isDate("2026-02-30")).isFalse();
        assertThat(FhirPrimitives.isDate("2026-13-01")).isFalse();
        assertThat(FhirPrimitives.isDate("2026-09")).isFalse();
    }

    @Test
    void dateOrDateTimeAcceptsDateInstantAndDateTime() {
        assertThat(FhirPrimitives.isDateOrDateTime("2026-09-11")).isTrue();
        assertThat(FhirPrimitives.isDateOrDateTime("2026-09-11T05:25:00Z")).isTrue();
        assertThat(FhirPrimitives.isDateOrDateTime("2026-09-11T05:25:00+05:30")).isTrue();
        assertThat(FhirPrimitives.isDateOrDateTime("nonsense")).isFalse();
        assertThat(FhirPrimitives.isDateOrDateTime(null)).isFalse();
    }

    @Test
    void quantityRequiresNonNullValueAndNonBlankUnitWhenPresent() {
        assertThat(FhirPrimitives.isValidQuantityValue(new BigDecimal("14.2"), "g/dL")).isTrue();
        assertThat(FhirPrimitives.isValidQuantityValue(BigDecimal.ZERO, null)).isTrue();
        assertThat(FhirPrimitives.isValidQuantityValue(null, "g/dL")).isFalse();
        assertThat(FhirPrimitives.isValidQuantityValue(new BigDecimal("14.2"), null)).isTrue();
        assertThat(FhirPrimitives.isValidQuantityValue(new BigDecimal("14.2"), " ")).isFalse();
        assertThat(FhirPrimitives.isValidQuantityValue(new BigDecimal("-1"), "mg")).isFalse();
    }

    @Test
    void resourceTypeRecognitionCoversSupportedTypes() {
        assertThat(FhirPrimitives.isSupportedResourceType("Patient")).isTrue();
        assertThat(FhirPrimitives.isSupportedResourceType("Encounter")).isTrue();
        assertThat(FhirPrimitives.isSupportedResourceType("Observation")).isTrue();
        assertThat(FhirPrimitives.isSupportedResourceType("DocumentReference")).isTrue();
        assertThat(FhirPrimitives.isSupportedResourceType("Consent")).isTrue();
        assertThat(FhirPrimitives.isSupportedResourceType("Practitioner")).isFalse();
        assertThat(FhirPrimitives.isSupportedResourceType(null)).isFalse();
    }
}