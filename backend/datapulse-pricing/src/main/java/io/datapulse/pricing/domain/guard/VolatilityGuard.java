package io.datapulse.pricing.domain.guard;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.stereotype.Component;

import io.datapulse.common.error.MessageCodes;
import io.datapulse.pricing.domain.GuardConfig;
import io.datapulse.pricing.domain.GuardResult;
import io.datapulse.pricing.domain.PricingSignalSet;

@Component
public class VolatilityGuard implements PricingGuard {

    @Override
    public String guardName() {
        return "volatility_guard";
    }

    @Override
    public int order() {
        return 21;
    }

    @Override
    public GuardResult check(PricingSignalSet signals, BigDecimal targetPrice, GuardConfig config) {
        if (!config.isVolatilityGuardEnabled()) {
            return GuardResult.pass(guardName());
        }

        if (signals.priceReversalsInPeriod() == null) {
            return GuardResult.pass(guardName());
        }

        int maxReversals = config.effectiveVolatilityReversals();
        int periodDays = config.effectiveVolatilityPeriodDays();

        if (signals.priceReversalsInPeriod() >= maxReversals) {
            return GuardResult.block(guardName(), MessageCodes.PRICING_GUARD_VOLATILITY,
                    Map.of("count", signals.priceReversalsInPeriod(),
                            "periodDays", periodDays,
                            "maxReversals", maxReversals));
        }
        return GuardResult.pass(guardName());
    }
}
