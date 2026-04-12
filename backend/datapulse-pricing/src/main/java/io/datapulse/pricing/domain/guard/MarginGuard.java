package io.datapulse.pricing.domain.guard;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

import org.springframework.stereotype.Component;

import io.datapulse.common.error.MessageCodes;
import io.datapulse.pricing.domain.GuardConfig;
import io.datapulse.pricing.domain.GuardResult;
import io.datapulse.pricing.domain.PricingSignalSet;

/**
 * Blocks when projected margin (after effective cost rate) falls below
 * the policy's min_margin_pct threshold.
 * <p>
 * Formula: projected_margin = (target − COGS − target × effective_cost_rate) / target
 * effective_cost_rate = commission + logistics_pct + return_rate + ad_cost
 * <p>
 * If min_margin_pct is not set on the policy (NULL) → guard is skipped.
 */
@Component
public class MarginGuard implements PricingGuard {

    @Override
    public String guardName() {
        return "margin_guard";
    }

    @Override
    public int order() {
        return 22;
    }

    @Override
    public GuardResult check(PricingSignalSet signals, BigDecimal targetPrice, GuardConfig config) {
        if (!config.isMarginGuardEnabled()) {
            return GuardResult.pass(guardName());
        }

        if (signals.cogs() == null || targetPrice == null
                || targetPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return GuardResult.pass(guardName());
        }

        BigDecimal effectiveCostRate = computeEffectiveCostRate(signals);

        BigDecimal revenue = targetPrice;
        BigDecimal costs = signals.cogs().add(revenue.multiply(effectiveCostRate));
        BigDecimal projectedMargin = revenue.subtract(costs)
                .divide(revenue, 4, RoundingMode.HALF_UP);

        BigDecimal threshold = config.effectiveMinMarginPct();
        if (threshold == null) {
            return GuardResult.pass(guardName());
        }

        if (projectedMargin.compareTo(threshold) < 0) {
            BigDecimal marginPct = projectedMargin.multiply(BigDecimal.valueOf(100))
                    .setScale(1, RoundingMode.HALF_UP);
            BigDecimal thresholdPct = threshold.multiply(BigDecimal.valueOf(100))
                    .setScale(1, RoundingMode.HALF_UP);
            return GuardResult.block(guardName(), MessageCodes.PRICING_GUARD_MARGIN_BELOW_THRESHOLD,
                    Map.of("marginPct", marginPct.toPlainString(),
                            "thresholdPct", thresholdPct.toPlainString(),
                            "targetPrice", targetPrice.toPlainString(),
                            "cogs", signals.cogs().toPlainString()));
        }

        return GuardResult.pass(guardName());
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
}
