package io.datapulse.pricing.domain.strategy;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.datapulse.common.error.MessageCodes;
import io.datapulse.pricing.domain.CompetitorAnchorParams;
import io.datapulse.pricing.domain.PolicySnapshot;
import io.datapulse.pricing.domain.PolicyType;
import io.datapulse.pricing.domain.PricingSignalSet;
import io.datapulse.pricing.domain.StrategyResult;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CompetitorAnchorStrategy implements PricingStrategy {

    private final ObjectMapper objectMapper;

    @Override
    public PolicyType strategyType() {
        return PolicyType.COMPETITOR_ANCHOR;
    }

    @Override
    public StrategyResult calculate(PricingSignalSet signals, PolicySnapshot policy) {
        CompetitorAnchorParams params = parseParams(policy.strategyParams());

        if (signals.competitorPrice() == null) {
            return StrategyResult.skip("No competitor data",
                    MessageCodes.PRICING_COMPETITOR_MISSING);
        }

        BigDecimal anchorPrice = signals.competitorPrice()
                .multiply(params.effectivePositionFactor())
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal targetPrice = anchorPrice;

        if (params.effectiveUseMarginFloor() && signals.cogs() != null
                && signals.cogs().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal effectiveCostRate = computeEffectiveCostRate(signals);
            BigDecimal denominator = BigDecimal.ONE
                    .subtract(params.effectiveMinMarginPct())
                    .subtract(effectiveCostRate);

            BigDecimal marginFloorPrice;
            if (denominator.compareTo(BigDecimal.ZERO) > 0) {
                marginFloorPrice = signals.cogs()
                        .divide(denominator, 2, RoundingMode.HALF_UP);
            } else {
                marginFloorPrice = signals.cogs()
                        .multiply(BigDecimal.valueOf(2))
                        .setScale(2, RoundingMode.HALF_UP);
            }

            targetPrice = anchorPrice.max(marginFloorPrice);
        }

        String explanation = buildExplanation(params, signals.competitorPrice(),
                anchorPrice, targetPrice, signals.cogs());

        return StrategyResult.success(targetPrice, explanation);
    }

    private BigDecimal computeEffectiveCostRate(PricingSignalSet signals) {
        BigDecimal rate = BigDecimal.ZERO;
        if (signals.avgCommissionPct() != null) {
            rate = rate.add(signals.avgCommissionPct());
        }
        if (signals.avgLogisticsPerUnit() != null && signals.currentPrice() != null
                && signals.currentPrice().compareTo(BigDecimal.ZERO) > 0) {
            rate = rate.add(signals.avgLogisticsPerUnit()
                    .divide(signals.currentPrice(), 4, RoundingMode.HALF_UP));
        }
        if (signals.returnRatePct() != null) {
            rate = rate.add(signals.returnRatePct());
        }
        if (signals.adCostRatio() != null) {
            rate = rate.add(signals.adCostRatio());
        }
        return rate;
    }

    private String buildExplanation(CompetitorAnchorParams params,
                                    BigDecimal competitorPrice,
                                    BigDecimal anchorPrice,
                                    BigDecimal targetPrice,
                                    BigDecimal cogs) {
        return ("competitor_price=%s, position_factor=%s, "
                + "anchor=%s, cogs=%s, min_margin=%s%%, "
                + "target=%s, raw=%s")
                .formatted(
                        competitorPrice.toPlainString(),
                        params.effectivePositionFactor().toPlainString(),
                        anchorPrice.toPlainString(),
                        cogs != null ? cogs.toPlainString() : "N/A",
                        params.effectiveMinMarginPct()
                                .multiply(BigDecimal.valueOf(100))
                                .toPlainString(),
                        targetPrice.toPlainString(),
                        targetPrice.toPlainString());
    }

    private CompetitorAnchorParams parseParams(String json) {
        try {
            return objectMapper.readValue(json, CompetitorAnchorParams.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Invalid COMPETITOR_ANCHOR strategy_params JSON", e);
        }
    }
}
