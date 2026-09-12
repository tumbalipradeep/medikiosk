package in.devmedi.kiosk;

import in.devmedi.kiosk.module.document.findings.normalize.DateParser;
import in.devmedi.kiosk.module.document.findings.normalize.UnitNormalizer;
import in.devmedi.kiosk.module.document.findings.normalize.ValueNormalizer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FindingsNormalizerTests {

    @Test
    void dayFirstDatesAreCanonicalisedDayFirst() {
        assertThat(DateParser.parse("05/06/2024")).contains("05/06/2024");
        assertThat(DateParser.parse("15-08-1990")).contains("15/08/1990");
        assertThat(DateParser.parse("7.3.2023")).contains("07/03/2023");
        assertThat(DateParser.parse("1/2/2024")).contains("01/02/2024");
    }

    @Test
    void twoDigitYearsArePreservedNotExpanded() {
        assertThat(DateParser.parse("05-06-24")).contains("05/06/24");
    }

    @Test
    void isoSamesAreReorderedDayFirst() {
        assertThat(DateParser.parse("2024-06-05")).contains("05/06/2024");
        assertThat(DateParser.parse("2024-6-5")).contains("05/06/2024");
    }

    @Test
    void namedMonthsAreNormalisedToThreeLetterAbbreviation() {
        assertThat(DateParser.parse("05 Jun 2024")).contains("05 Jun 2024");
        assertThat(DateParser.parse("5 June, 2024")).contains("05 Jun 2024");
        assertThat(DateParser.parse("23-Dec-2023")).contains("23 Dec 2023");
    }

    @Test
    void nonDatesAreRejectedNotGuessed() {
        assertThat(DateParser.parse("not a date")).isEmpty();
        assertThat(DateParser.parse("")).isEmpty();
        assertThat(DateParser.parse(null)).isEmpty();
        assertThat(DateParser.parse("June 2024")).isEmpty();
    }

    @Test
    void valueNormalizerStripsThousandsAndPlusSigns() {
        assertThat(ValueNormalizer.normalize("6,500")).contains("6500");
        assertThat(ValueNormalizer.normalize("2.5")).contains("2.5");
        assertThat(ValueNormalizer.normalize("+3.0")).contains("3.0");
        assertThat(ValueNormalizer.normalize("abc")).isEmpty();
        assertThat(ValueNormalizer.normalize("-5")).isEmpty();
    }

    @Test
    void valueNormalizerTreatsGroupedCommasAsThousandsButLoneCommaAsDecimal() {
        assertThat(ValueNormalizer.normalize("12,500")).contains("12500");
        assertThat(ValueNormalizer.normalize("1,234,567.89")).contains("1234567.89");
        assertThat(ValueNormalizer.normalize("13,5")).contains("13.5");
        assertThat(ValueNormalizer.normalize("4,789,0")).isEmpty();
    }

    @Test
    void unitNormalizerMapsEquivalentSpellingsOnly() {
        assertThat(UnitNormalizer.normalize("g/dl")).isEqualTo("g/dL");
        assertThat(UnitNormalizer.normalize("mg/dl")).isEqualTo("mg/dL");
        assertThat(UnitNormalizer.normalize("mmol/l")).isEqualTo("mmol/L");
        assertThat(UnitNormalizer.normalize("kg/m2")).isEqualTo("kg/m²");
        assertThat(UnitNormalizer.normalize("deg F")).isEqualTo("°F");
        assertThat(UnitNormalizer.normalize("degree c")).isEqualTo("°C");
        assertThat(UnitNormalizer.normalize("uL")).isEqualTo("uL");
        assertThat(UnitNormalizer.normalize("/mcL")).isEqualTo("/µL");
    }

    @Test
    void unitNormalizerNeverInventsUnitsAndLeavesUnknownAsIs() {
        assertThat(UnitNormalizer.normalize("lakh/cu.mm")).isEqualTo("lakh/cu.mm");
        assertThat(UnitNormalizer.normalize(null)).isNull();
        assertThat(UnitNormalizer.normalize("  ")).isNull();
    }
}