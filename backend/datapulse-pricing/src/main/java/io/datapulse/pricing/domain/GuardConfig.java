package io.datapulse.pricing.domain;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GuardConfig(
        @JsonProperty("margin_guard_enabled") Boolean marginGuardEnabled,
        @JsonProperty("frequency_guard_enabled") Boolean frequencyGuardEnabled,
        @JsonProperty("frequency_guard_hours") Integer frequencyGuardHours,
        @JsonProperty("volatility_guard_enabled") Boolean volatilityGuardEnabled,
        @JsonProperty("volatility_guard_reversals") Integer volatilityGuardReversals,
        @JsonProperty("volatility_guard_period_days") Integer volatilityGuardPeriodDays,
        @JsonProperty("promo_guard_enabled") Boolean promoGuardEnabled,
        @JsonProperty("stock_out_guard_enabled") Boolean stockOutGuardEnabled,
        @JsonProperty("stale_data_guard_hours") Integer staleDataGuardHours,
        @JsonProperty("ad_cost_guard_enabled") Boolean adCostGuardEnabled,
        @JsonProperty("ad_cost_drr_threshold_pct") BigDecimal adCostDrrThresholdPct,
        @JsonProperty("competitor_freshness_guard_enabled") Boolean competitorFreshnessGuardEnabled,
        @JsonProperty("competitor_freshness_hours") Integer competitorFreshnessHours,
        @JsonProperty("competitor_trust_guard_enabled") Boolean competitorTrustGuardEnabled,
        @JsonIgnore BigDecimal minMarginPct
) {

    private static final BigDecimal DEFAULT_AD_COST_DRR_THRESHOLD = new BigDecimal("0.15");

    public GuardConfig(Boolean marginGuardEnabled, Boolean frequencyGuardEnabled,
                       Integer frequencyGuardHours, Boolean volatilityGuardEnabled,
                       Integer volatilityGuardReversals, Integer volatilityGuardPeriodDays,
                       Boolean promoGuardEnabled, Boolean stockOutGuardEnabled,
                       Integer staleDataGuardHours, Boolean adCostGuardEnabled,
                       BigDecimal adCostDrrThresholdPct) {
        this(marginGuardEnabled, frequencyGuardEnabled, frequencyGuardHours,
                volatilityGuardEnabled, volatilityGuardReversals, volatilityGuardPeriodDays,
                promoGuardEnabled, stockOutGuardEnabled, staleDataGuardHours,
                adCostGuardEnabled, adCostDrrThresholdPct,
                null, null, null, null);
    }

    public static final GuardConfig DEFAULTS = new GuardConfig(
            true, true, 24, true, 3, 7, true, true, 24,
            false, null, null, null, null, null
    );

    public GuardConfig withMinMarginPct(BigDecimal pct) {
        return new GuardConfig(marginGuardEnabled, frequencyGuardEnabled, frequencyGuardHours,
                volatilityGuardEnabled, volatilityGuardReversals, volatilityGuardPeriodDays,
                promoGuardEnabled, stockOutGuardEnabled, staleDataGuardHours,
                adCostGuardEnabled, adCostDrrThresholdPct,
                competitorFreshnessGuardEnabled, competitorFreshnessHours,
                competitorTrustGuardEnabled, pct);
    }

    public BigDecimal effectiveMinMarginPct() {
        return minMarginPct;
    }

    public boolean isMarginGuardEnabled() {
        return marginGuardEnabled == null || marginGuardEnabled;
    }

    public boolean isFrequencyGuardEnabled() {
        return frequencyGuardEnabled == null || frequencyGuardEnabled;
    }

    public int effectiveFrequencyGuardHours() {
        return frequencyGuardHours != null ? frequencyGuardHours : 24;
    }

    public boolean isVolatilityGuardEnabled() {
        return volatilityGuardEnabled == null || volatilityGuardEnabled;
    }

    public int effectiveVolatilityReversals() {
        return volatilityGuardReversals != null ? volatilityGuardReversals : 3;
    }

    public int effectiveVolatilityPeriodDays() {
        return volatilityGuardPeriodDays != null ? volatilityGuardPeriodDays : 7;
    }

    public boolean isPromoGuardEnabled() {
        return promoGuardEnabled == null || promoGuardEnabled;
    }

    public boolean isStockOutGuardEnabled() {
        return stockOutGuardEnabled == null || stockOutGuardEnabled;
    }

    public int effectiveStaleDataGuardHours() {
        return staleDataGuardHours != null ? staleDataGuardHours : 24;
    }

    public boolean isAdCostGuardEnabled() {
        return adCostGuardEnabled != null && adCostGuardEnabled;
    }

    public BigDecimal effectiveAdCostDrrThreshold() {
        return adCostDrrThresholdPct != null ? adCostDrrThresholdPct : DEFAULT_AD_COST_DRR_THRESHOLD;
    }

    public boolean isCompetitorFreshnessGuardEnabled() {
        return competitorFreshnessGuardEnabled != null && competitorFreshnessGuardEnabled;
    }

    public int effectiveCompetitorFreshnessHours() {
        return competitorFreshnessHours != null ? competitorFreshnessHours : 72;
    }

    public boolean isCompetitorTrustGuardEnabled() {
        return competitorTrustGuardEnabled != null && competitorTrustGuardEnabled;
    }
}
