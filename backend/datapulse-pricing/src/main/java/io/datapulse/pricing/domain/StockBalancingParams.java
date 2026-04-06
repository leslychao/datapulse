package io.datapulse.pricing.domain;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record StockBalancingParams(
    Integer criticalDaysOfCover,
    Integer overstockDaysOfCover,
    BigDecimal stockoutMarkupPct,
    BigDecimal overstockDiscountFactor,
    BigDecimal maxDiscountPct,
    Integer leadTimeDays,
    BigDecimal roundingStep,
    TargetMarginParams.RoundingDirection roundingDirection
) {

    public int effectiveCriticalDaysOfCover() {
        return criticalDaysOfCover != null ? criticalDaysOfCover : 7;
    }

    public int effectiveOverstockDaysOfCover() {
        return overstockDaysOfCover != null ? overstockDaysOfCover : 60;
    }

    public BigDecimal effectiveStockoutMarkupPct() {
        return stockoutMarkupPct != null
            ? stockoutMarkupPct : new BigDecimal("0.05");
    }

    public BigDecimal effectiveOverstockDiscountFactor() {
        return overstockDiscountFactor != null
            ? overstockDiscountFactor : new BigDecimal("0.10");
    }

    public BigDecimal effectiveMaxDiscountPct() {
        return maxDiscountPct != null
            ? maxDiscountPct : new BigDecimal("0.20");
    }

    public int effectiveLeadTimeDays() {
        return leadTimeDays != null ? leadTimeDays : 14;
    }
}
