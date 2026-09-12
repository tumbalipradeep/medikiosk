package in.devmedi.kiosk.module.document.findings.analysis;

import in.devmedi.kiosk.module.document.findings.model.AbnormalityStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LabAbnormalityEvaluatorTests {

    @Test
    void valueInsideRangeIsNormal() {
        assertThat(LabAbnormalityEvaluator.evaluate("13.5", "13.0 - 17.0"))
                .isEqualTo(AbnormalityStatus.NORMAL);
    }

    @Test
    void valueBelowRangeIsLow() {
        assertThat(LabAbnormalityEvaluator.evaluate("2.9", "3.5 - 6.0"))
                .isEqualTo(AbnormalityStatus.LOW);
    }

    @Test
    void valueAboveRangeIsHigh() {
        assertThat(LabAbnormalityEvaluator.evaluate("220", "140 - 200"))
                .isEqualTo(AbnormalityStatus.HIGH);
    }

    @Test
    void lowerBoundaryIsNormal() {
        assertThat(LabAbnormalityEvaluator.evaluate("13.0", "13.0 - 17.0"))
                .isEqualTo(AbnormalityStatus.NORMAL);
    }

    @Test
    void upperBoundaryIsNormal() {
        assertThat(LabAbnormalityEvaluator.evaluate("17.0", "13.0 - 17.0"))
                .isEqualTo(AbnormalityStatus.NORMAL);
    }

    @Test
    void enDashRangeIsSupported() {
        assertThat(LabAbnormalityEvaluator.evaluate("5.8", "4.0\u20135.6"))
                .isEqualTo(AbnormalityStatus.HIGH);
        assertThat(LabAbnormalityEvaluator.evaluate("4.5", "4.0\u20135.6"))
                .isEqualTo(AbnormalityStatus.NORMAL);
    }

    @Test
    void emDashRangeIsSupported() {
        assertThat(LabAbnormalityEvaluator.evaluate("3.9", "4.0\u20145.6"))
                .isEqualTo(AbnormalityStatus.LOW);
    }

    @Test
    void commaSeparatedNumbersAreParsed() {
        assertThat(LabAbnormalityEvaluator.evaluate("150,000", "150000 - 450000"))
                .isEqualTo(AbnormalityStatus.NORMAL);
        assertThat(LabAbnormalityEvaluator.evaluate("120,000", "150,000 - 450,000"))
                .isEqualTo(AbnormalityStatus.LOW);
    }

    @Test
    void commaDecimalValueIsTreatedAsDecimalPointNotThousands() {
        assertThat(LabAbnormalityEvaluator.evaluate("13,5", "13.0 - 17.0"))
                .isEqualTo(AbnormalityStatus.NORMAL);
        assertThat(LabAbnormalityEvaluator.evaluate("2,9", "3.5 - 6.0"))
                .isEqualTo(AbnormalityStatus.LOW);
        assertThat(LabAbnormalityEvaluator.evaluate("4,0", "3.5 - 6.0"))
                .isEqualTo(AbnormalityStatus.NORMAL);
    }

    @Test
    void trailingUnitTextAfterRangeIsIgnored() {
        assertThat(LabAbnormalityEvaluator.evaluate("2.5", "1.5 - 4.5 lakh/cu.mm"))
                .isEqualTo(AbnormalityStatus.NORMAL);
        assertThat(LabAbnormalityEvaluator.evaluate("5.2", "1.5 - 4.5 lakh/cu.mm"))
                .isEqualTo(AbnormalityStatus.HIGH);
    }

    @Test
    void strictUpperDirectionalRangeTreatsBoundaryAsAbnormal() {
        assertThat(LabAbnormalityEvaluator.evaluate("220", "< 200"))
                .isEqualTo(AbnormalityStatus.HIGH);
        assertThat(LabAbnormalityEvaluator.evaluate("200", "< 200"))
                .isEqualTo(AbnormalityStatus.HIGH);
        assertThat(LabAbnormalityEvaluator.evaluate("180", "< 200"))
                .isEqualTo(AbnormalityStatus.NORMAL);
    }

    @Test
    void inclusiveUpperDirectionalRangeTreatsBoundaryAsNormal() {
        assertThat(LabAbnormalityEvaluator.evaluate("100", "<= 100"))
                .isEqualTo(AbnormalityStatus.NORMAL);
        assertThat(LabAbnormalityEvaluator.evaluate("112", "<= 100"))
                .isEqualTo(AbnormalityStatus.HIGH);
    }

    @Test
    void strictLowerDirectionalRangeTreatsBoundaryAsAbnormal() {
        assertThat(LabAbnormalityEvaluator.evaluate("5.0", "> 5.0"))
                .isEqualTo(AbnormalityStatus.LOW);
        assertThat(LabAbnormalityEvaluator.evaluate("6.2", "> 5.0"))
                .isEqualTo(AbnormalityStatus.NORMAL);
    }

    @Test
    void inclusiveLowerDirectionalRangeTreatsBoundaryAsNormal() {
        assertThat(LabAbnormalityEvaluator.evaluate("90", ">= 90"))
                .isEqualTo(AbnormalityStatus.NORMAL);
        assertThat(LabAbnormalityEvaluator.evaluate("84", ">= 90"))
                .isEqualTo(AbnormalityStatus.LOW);
    }

    @Test
    void missingValueOrRangeIsUnknown() {
        assertThat(LabAbnormalityEvaluator.evaluate(null, "13.0 - 17.0"))
                .isEqualTo(AbnormalityStatus.UNKNOWN);
        assertThat(LabAbnormalityEvaluator.evaluate("13.5", null))
                .isEqualTo(AbnormalityStatus.UNKNOWN);
        assertThat(LabAbnormalityEvaluator.evaluate("13.5", "  "))
                .isEqualTo(AbnormalityStatus.UNKNOWN);
        assertThat(LabAbnormalityEvaluator.evaluate("abc", "13.0 - 17.0"))
                .isEqualTo(AbnormalityStatus.UNKNOWN);
        assertThat(LabAbnormalityEvaluator.evaluate("13.5", "approx 10 - 20"))
                .isEqualTo(AbnormalityStatus.UNKNOWN);
    }

    @Test
    void invertedRangeIsUnknown() {
        assertThat(LabAbnormalityEvaluator.evaluate("13.5", "17.0 - 13.0"))
                .isEqualTo(AbnormalityStatus.UNKNOWN);
    }

    @Test
    void evaluationIsDeterministic() {
        assertThat(LabAbnormalityEvaluator.evaluateRepeated("13.5", "13.0 - 17.0"))
                .isEqualTo(AbnormalityStatus.NORMAL);
    }
}