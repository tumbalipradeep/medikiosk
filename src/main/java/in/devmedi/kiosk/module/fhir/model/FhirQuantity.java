package in.devmedi.kiosk.module.fhir.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

/**
 * Minimal FHIR R4 {@code Quantity} for observation values. Only value and unit
 * are populated; UCUM mapping is deliberately deferred, so no {@code system}/
 * {@code code} pair is invented.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FhirQuantity(BigDecimal value,
                           String unit,
                           String system,
                           String code) {

    public static FhirQuantity of(BigDecimal value, String unit) {
        return new FhirQuantity(value, unit, null, null);
    }
}