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
 * Blocks price DECREASE when ad-cost DRR (доля рекламных расходов)
 * exceeds the configured threshold.
 * <p>
 * Price increases are never blocked — raising the price when DRR is high
 * is the desired corrective action (DD-AD-14).
 */
@Component
public class AdCostGuard implements PricingGuard {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    @Override
    public String guardName() {
        return "ad_cost_drr";
    }

    @Override
    public int order() {
        return 70;
    }

    @Override
    public GuardResult check(PricingSignalSet signals, BigDecimal targetPrice, GuardConfig config) {
        if (!config.isAdCostGuardEnabled()) {
            return GuardResult.pass(guardName());
        }

        if (signals.currentPrice() == null || targetPrice == null
                || targetPrice.compareTo(signals.currentPrice()) >= 0) {
            return GuardResult.pass(guardName());
        }

        if (signals.adCostRatio() == null) {
            return GuardResult.pass(guardName());
        }

        BigDecimal threshold = config.effectiveAdCostDrrThreshold();
        if (signals.adCostRatio().compareTo(threshold) > 0) {
            BigDecimal drrPct = signals.adCostRatio().multiply(HUNDRED)
                    .setScale(1, RoundingMode.HALF_UP);
            BigDecimal thresholdPct = threshold.multiply(HUNDRED)
                    .setScale(1, RoundingMode.HALF_UP);
            return GuardResult.block(guardName(),
                    MessageCodes.PRICING_GUARD_AD_COST_DRR_BLOCKED,
                    Map.of("drrPct", drrPct.toPlainString(),
                            "threshold", thresholdPct.toPlainString()));
        }

        return GuardResult.pass(guardName());
    }
}
