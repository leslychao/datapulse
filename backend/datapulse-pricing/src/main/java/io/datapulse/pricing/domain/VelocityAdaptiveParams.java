package io.datapulse.pricing.domain;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record VelocityAdaptiveParams(
    BigDecimal decelerationThreshold,
    BigDecimal accelerationThreshold,
    BigDecimal decelerationDiscountPct,
    BigDecimal accelerationMarkupPct,
    Integer minBaselineSales,
    Integer velocityWindowShortDays,
    Integer velocityWindowLongDays,
    BigDecimal roundingStep,
    TargetMarginParams.RoundingDirection roundingDirection
) {

    public BigDecimal effectiveDecelerationThreshold() {
        return decelerationThreshold != null
            ? decelerationThreshold : new BigDecimal("0.70");
    }

    public BigDecimal effectiveAccelerationThreshold() {
        return accelerationThreshold != null
            ? accelerationThreshold : new BigDecimal("1.30");
    }

    public BigDecimal effectiveDecelerationDiscountPct() {
        return decelerationDiscountPct != null
            ? decelerationDiscountPct : new BigDecimal("0.05");
    }

    public BigDecimal effectiveAccelerationMarkupPct() {
        return accelerationMarkupPct != null
            ? accelerationMarkupPct : new BigDecimal("0.03");
    }

    public int effectiveMinBaselineSales() {
        return minBaselineSales != null ? minBaselineSales : 10;
    }

    public int effectiveVelocityWindowShortDays() {
        return velocityWindowShortDays != null ? velocityWindowShortDays : 7;
    }

    public int effectiveVelocityWindowLongDays() {
        return velocityWindowLongDays != null ? velocityWindowLongDays : 30;
    }
}
