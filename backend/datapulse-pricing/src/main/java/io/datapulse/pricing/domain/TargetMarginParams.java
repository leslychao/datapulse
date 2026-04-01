package io.datapulse.pricing.domain;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TargetMarginParams(
        BigDecimal targetMarginPct,
        CommissionSource commissionSource,
        BigDecimal commissionManualPct,
        Integer commissionLookbackDays,
        Integer commissionMinTransactions,
        LogisticsSource logisticsSource,
        BigDecimal logisticsManualAmount,
        Boolean includeReturnAdjustment,
        Boolean includeAdCost,
        BigDecimal roundingStep,
        RoundingDirection roundingDirection
) {

    public enum CommissionSource {
        AUTO, MANUAL, AUTO_WITH_MANUAL_FALLBACK
    }

    public enum LogisticsSource {
        AUTO, MANUAL, AUTO_WITH_MANUAL_FALLBACK
    }

    public enum RoundingDirection {
        FLOOR, NEAREST, CEIL
    }

    public int effectiveLookbackDays() {
        return commissionLookbackDays != null ? commissionLookbackDays : 30;
    }

    public int effectiveMinTransactions() {
        return commissionMinTransactions != null ? commissionMinTransactions : 5;
    }

    public BigDecimal effectiveRoundingStep() {
        return roundingStep != null ? roundingStep : BigDecimal.TEN;
    }

    public RoundingDirection effectiveRoundingDirection() {
        return roundingDirection != null ? roundingDirection : RoundingDirection.FLOOR;
    }

    public CommissionSource effectiveCommissionSource() {
        return commissionSource != null ? commissionSource : CommissionSource.AUTO_WITH_MANUAL_FALLBACK;
    }

    public LogisticsSource effectiveLogisticsSource() {
        return logisticsSource != null ? logisticsSource : LogisticsSource.AUTO_WITH_MANUAL_FALLBACK;
    }

    public boolean effectiveIncludeReturnAdjustment() {
        return includeReturnAdjustment != null && includeReturnAdjustment;
    }

    public boolean effectiveIncludeAdCost() {
        return includeAdCost != null && includeAdCost;
    }
}
