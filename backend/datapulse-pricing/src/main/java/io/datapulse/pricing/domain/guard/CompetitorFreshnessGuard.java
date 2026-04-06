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
public class CompetitorFreshnessGuard implements PricingGuard {

    @Override
    public String guardName() {
        return "competitor_freshness_guard";
    }

    @Override
    public int order() {
        return 25;
    }

    @Override
    public GuardResult check(PricingSignalSet signals, BigDecimal targetPrice,
                             GuardConfig config) {
        if (!config.isCompetitorFreshnessGuardEnabled()) {
            return GuardResult.pass(guardName());
        }

        if (signals.competitorFreshnessAt() == null) {
            return GuardResult.pass(guardName());
        }

        int thresholdHours = config.effectiveCompetitorFreshnessHours();
        OffsetDateTime threshold = OffsetDateTime.now().minusHours(thresholdHours);

        if (signals.competitorFreshnessAt().isBefore(threshold)) {
            return GuardResult.block(guardName(),
                    MessageCodes.PRICING_COMPETITOR_STALE,
                    Map.of("hours", thresholdHours,
                            "lastObserved", signals.competitorFreshnessAt().toString()));
        }

        return GuardResult.pass(guardName());
    }
}
