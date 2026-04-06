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
import io.datapulse.pricing.domain.StockBalancingParams;
import io.datapulse.pricing.domain.StrategyResult;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class StockBalancingStrategy implements PricingStrategy {

    private final ObjectMapper objectMapper;

    @Override
    public PolicyType strategyType() {
        return PolicyType.STOCK_BALANCING;
    }

    @Override
    public StrategyResult calculate(PricingSignalSet signals, PolicySnapshot policy) {
        StockBalancingParams params = parseParams(policy.strategyParams());

        if (signals.currentPrice() == null
                || signals.currentPrice().compareTo(BigDecimal.ZERO) <= 0) {
            return StrategyResult.skip("Current price not available",
                    MessageCodes.PRICING_CURRENT_PRICE_MISSING);
        }

        if (signals.daysOfCover() == null) {
            return StrategyResult.skip("No inventory data",
                    MessageCodes.PRICING_STOCK_NO_DATA);
        }

        BigDecimal daysOfCover = signals.daysOfCover();
        int criticalThreshold = params.effectiveCriticalDaysOfCover();
        int overstockThreshold = params.effectiveOverstockDaysOfCover();

        BigDecimal adjustment;

        if (daysOfCover.compareTo(BigDecimal.valueOf(criticalThreshold)) < 0) {
            adjustment = params.effectiveStockoutMarkupPct();

            BigDecimal rawTargetPrice = signals.currentPrice()
                    .multiply(BigDecimal.ONE.add(adjustment))
                    .setScale(2, RoundingMode.HALF_UP);

            String explanation = buildNearStockoutExplanation(
                    daysOfCover, criticalThreshold, adjustment, rawTargetPrice);
            return StrategyResult.success(rawTargetPrice, explanation);

        } else if (daysOfCover.compareTo(
                BigDecimal.valueOf(overstockThreshold)) > 0) {
            BigDecimal overshoot = daysOfCover
                    .subtract(BigDecimal.valueOf(overstockThreshold))
                    .divide(BigDecimal.valueOf(overstockThreshold),
                            4, RoundingMode.HALF_UP);

            BigDecimal discount = overshoot
                    .multiply(params.effectiveOverstockDiscountFactor())
                    .min(params.effectiveMaxDiscountPct());

            adjustment = discount.negate();

            BigDecimal rawTargetPrice = signals.currentPrice()
                    .multiply(BigDecimal.ONE.add(adjustment))
                    .setScale(2, RoundingMode.HALF_UP);

            String explanation = buildOverstockExplanation(
                    daysOfCover, overstockThreshold, overshoot,
                    params.effectiveOverstockDiscountFactor(),
                    discount, adjustment, rawTargetPrice);
            return StrategyResult.success(rawTargetPrice, explanation);

        } else {
            return StrategyResult.skip(
                    buildNormalExplanation(daysOfCover, criticalThreshold,
                            overstockThreshold),
                    MessageCodes.PRICING_STOCK_NORMAL);
        }
    }

    private String buildNearStockoutExplanation(
            BigDecimal daysOfCover, int criticalThreshold,
            BigDecimal adjustment, BigDecimal rawPrice) {
        return ("days_of_cover=%s, critical_threshold=%d, "
                + "adjustment=%+.1f%%, raw=%s")
                .formatted(
                        daysOfCover.setScale(1, RoundingMode.HALF_UP)
                                .toPlainString(),
                        criticalThreshold,
                        adjustment.multiply(BigDecimal.valueOf(100))
                                .doubleValue(),
                        rawPrice.toPlainString());
    }

    private String buildOverstockExplanation(
            BigDecimal daysOfCover, int overstockThreshold,
            BigDecimal overshoot, BigDecimal discountFactor,
            BigDecimal discount, BigDecimal adjustment,
            BigDecimal rawPrice) {
        return ("days_of_cover=%s, overstock_threshold=%d, "
                + "overshoot=%.1f%%, discount_factor=%s, "
                + "adjustment=%+.1f%%, raw=%s")
                .formatted(
                        daysOfCover.setScale(1, RoundingMode.HALF_UP)
                                .toPlainString(),
                        overstockThreshold,
                        overshoot.multiply(BigDecimal.valueOf(100))
                                .doubleValue(),
                        discountFactor.toPlainString(),
                        adjustment.multiply(BigDecimal.valueOf(100))
                                .doubleValue(),
                        rawPrice.toPlainString());
    }

    private String buildNormalExplanation(
            BigDecimal daysOfCover, int criticalThreshold,
            int overstockThreshold) {
        return ("days_of_cover=%s — within [%d, %d], normal")
                .formatted(
                        daysOfCover.setScale(1, RoundingMode.HALF_UP)
                                .toPlainString(),
                        criticalThreshold,
                        overstockThreshold);
    }

    private StockBalancingParams parseParams(String json) {
        try {
            return objectMapper.readValue(json, StockBalancingParams.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Invalid STOCK_BALANCING strategy_params JSON", e);
        }
    }
}
