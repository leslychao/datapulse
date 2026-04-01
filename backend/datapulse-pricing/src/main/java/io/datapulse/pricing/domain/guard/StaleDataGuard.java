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
public class StaleDataGuard implements PricingGuard {

    @Override
    public String guardName() {
        return "stale_data_guard";
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public GuardResult check(PricingSignalSet signals, BigDecimal targetPrice, GuardConfig config) {
        if (signals.dataFreshnessAt() == null) {
            return GuardResult.block(guardName(), MessageCodes.PRICING_GUARD_STALE_DATA_UNKNOWN);
        }

        int thresholdHours = config.effectiveStaleDataGuardHours();
        OffsetDateTime threshold = OffsetDateTime.now().minusHours(thresholdHours);

        if (signals.dataFreshnessAt().isBefore(threshold)) {
            return GuardResult.block(guardName(), MessageCodes.PRICING_GUARD_STALE_DATA_STALE,
                    Map.of("hours", thresholdHours,
                            "lastSync", signals.dataFreshnessAt().toString()));
        }
        return GuardResult.pass(guardName());
    }
}
