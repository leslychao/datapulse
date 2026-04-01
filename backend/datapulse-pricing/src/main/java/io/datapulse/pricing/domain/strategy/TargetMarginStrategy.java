package io.datapulse.pricing.domain.strategy;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.datapulse.common.error.MessageCodes;
import io.datapulse.pricing.domain.PolicySnapshot;
import io.datapulse.pricing.domain.PolicyType;
import io.datapulse.pricing.domain.PricingSignalSet;
import io.datapulse.pricing.domain.StrategyResult;
import io.datapulse.pricing.domain.TargetMarginParams;
import io.datapulse.pricing.domain.TargetMarginParams.CommissionSource;
import io.datapulse.pricing.domain.TargetMarginParams.LogisticsSource;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TargetMarginStrategy implements PricingStrategy {

    private final ObjectMapper objectMapper;

    @Override
    public PolicyType strategyType() {
        return PolicyType.TARGET_MARGIN;
    }

    @Override
    public StrategyResult calculate(PricingSignalSet signals, PolicySnapshot policy) {
        TargetMarginParams params = parseParams(policy.strategyParams());

        if (signals.cogs() == null || signals.cogs().compareTo(BigDecimal.ZERO) <= 0) {
            return StrategyResult.skip("COGS not available", MessageCodes.PRICING_COGS_MISSING);
        }

        BigDecimal commissionPct = resolveCommission(params, signals);
        if (commissionPct == null) {
            return StrategyResult.skip("Commission rate not available",
                    MessageCodes.PRICING_COMMISSION_MISSING);
        }

        BigDecimal logisticsPct = resolveLogisticsPct(params, signals);
        BigDecimal returnAdjPct = params.effectiveIncludeReturnAdjustment() && signals.returnRatePct() != null
                ? signals.returnRatePct()
                : BigDecimal.ZERO;
        BigDecimal adCostPct = params.effectiveIncludeAdCost() && signals.adCostRatio() != null
                ? signals.adCostRatio()
                : BigDecimal.ZERO;

        BigDecimal effectiveCostRate = commissionPct
                .add(logisticsPct)
                .add(returnAdjPct)
                .add(adCostPct);

        BigDecimal denominator = BigDecimal.ONE
                .subtract(params.targetMarginPct())
                .subtract(effectiveCostRate);

        if (denominator.compareTo(BigDecimal.ZERO) <= 0) {
            return StrategyResult.skip(
                    "target margin + effective cost rate >= 100%% (denominator=%.4f)"
                            .formatted(denominator),
                    MessageCodes.PRICING_DENOMINATOR_INVALID);
        }

        BigDecimal rawPrice = signals.cogs().divide(denominator, 2, RoundingMode.HALF_UP);

        String explanation = buildExplanation(params, commissionPct, logisticsPct,
                returnAdjPct, adCostPct, effectiveCostRate, rawPrice);

        return StrategyResult.success(rawPrice, explanation);
    }

    private BigDecimal resolveCommission(TargetMarginParams params, PricingSignalSet signals) {
        CommissionSource source = params.effectiveCommissionSource();

        return switch (source) {
            case AUTO -> signals.avgCommissionPct();
            case MANUAL -> params.commissionManualPct();
            case AUTO_WITH_MANUAL_FALLBACK -> {
                if (signals.avgCommissionPct() != null) {
                    yield signals.avgCommissionPct();
                }
                yield params.commissionManualPct();
            }
        };
    }

    private BigDecimal resolveLogisticsPct(TargetMarginParams params, PricingSignalSet signals) {
        LogisticsSource source = params.effectiveLogisticsSource();

        BigDecimal autoValue = signals.avgLogisticsPerUnit();
        BigDecimal manualValue = params.logisticsManualAmount();

        BigDecimal resolved = switch (source) {
            case AUTO -> autoValue;
            case MANUAL -> manualValue;
            case AUTO_WITH_MANUAL_FALLBACK -> autoValue != null ? autoValue : manualValue;
        };

        if (resolved == null) {
            return BigDecimal.ZERO;
        }

        if (signals.cogs() != null && signals.cogs().compareTo(BigDecimal.ZERO) > 0
                && signals.currentPrice() != null && signals.currentPrice().compareTo(BigDecimal.ZERO) > 0) {
            return resolved.divide(signals.currentPrice(), 4, RoundingMode.HALF_UP);
        }

        return BigDecimal.ZERO;
    }

    private String buildExplanation(TargetMarginParams params,
                                    BigDecimal commissionPct, BigDecimal logisticsPct,
                                    BigDecimal returnAdjPct, BigDecimal adCostPct,
                                    BigDecimal effectiveCostRate, BigDecimal rawPrice) {
        return ("target_margin=%.1f%%, effective_cost_rate=%.1f%% "
                + "(commission=%.1f%%, logistics=%.1f%%, returns=%.1f%%, ads=%.1f%%) → raw=%s")
                .formatted(
                        pctDisplay(params.targetMarginPct()),
                        pctDisplay(effectiveCostRate),
                        pctDisplay(commissionPct),
                        pctDisplay(logisticsPct),
                        pctDisplay(returnAdjPct),
                        pctDisplay(adCostPct),
                        rawPrice.toPlainString());
    }

    private double pctDisplay(BigDecimal value) {
        return value.multiply(BigDecimal.valueOf(100)).doubleValue();
    }

    private TargetMarginParams parseParams(String json) {
        try {
            return objectMapper.readValue(json, TargetMarginParams.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid TARGET_MARGIN strategy_params JSON", e);
        }
    }
}
