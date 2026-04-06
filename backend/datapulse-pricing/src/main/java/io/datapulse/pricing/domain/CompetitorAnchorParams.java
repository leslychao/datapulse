package io.datapulse.pricing.domain;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CompetitorAnchorParams(
        BigDecimal positionFactor,
        BigDecimal minMarginPct,
        CompetitorPriceAggregation aggregation,
        Boolean useMarginFloor,
        BigDecimal roundingStep,
        TargetMarginParams.RoundingDirection roundingDirection
) {

    public enum CompetitorPriceAggregation {
        MIN, MEDIAN, AVG
    }

    public BigDecimal effectivePositionFactor() {
        return positionFactor != null ? positionFactor : BigDecimal.ONE;
    }

    public BigDecimal effectiveMinMarginPct() {
        return minMarginPct != null ? minMarginPct : new BigDecimal("0.10");
    }

    public CompetitorPriceAggregation effectiveAggregation() {
        return aggregation != null ? aggregation : CompetitorPriceAggregation.MIN;
    }

    public boolean effectiveUseMarginFloor() {
        return useMarginFloor == null || useMarginFloor;
    }
}
