package io.datapulse.pricing.domain.guard;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

import org.springframework.stereotype.Component;

import io.datapulse.common.error.MessageCodes;
import io.datapulse.pricing.domain.GuardConfig;
import io.datapulse.pricing.domain.GuardResult;
import io.datapulse.pricing.domain.PricingSignalSet;

@Component
public class FrequencyGuard implements PricingGuard {

    @Override
    public String guardName() {
        return "frequency_guard";
    }

    @Override
    public int order() {
        return 50;
    }

    @Override
    public GuardResult check(PricingSignalSet signals, BigDecimal targetPrice, GuardConfig config) {
        if (!config.isFrequencyGuardEnabled()) {
            return GuardResult.pass(guardName());
        }

        if (signals.lastPriceChangeAt() == null) {
            return GuardResult.pass(guardName());
        }

        int thresholdHours = config.effectiveFrequencyGuardHours();
        OffsetDateTime threshold = OffsetDateTime.now().minusHours(thresholdHours);

        if (signals.lastPriceChangeAt().isAfter(threshold)) {
            return GuardResult.block(guardName(), MessageCodes.PRICING_GUARD_FREQUENCY,
                    Map.of("hours", thresholdHours,
                            "lastChangeAt", signals.lastPriceChangeAt().toString()));
        }
        return GuardResult.pass(guardName());
    }
}
