package io.datapulse.pricing.domain.guard;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.stereotype.Component;

import io.datapulse.common.error.MessageCodes;
import io.datapulse.pricing.domain.GuardConfig;
import io.datapulse.pricing.domain.GuardResult;
import io.datapulse.pricing.domain.PricingSignalSet;

@Component
public class CompetitorTrustGuard implements PricingGuard {

    @Override
    public String guardName() {
        return "competitor_trust_guard";
    }

    @Override
    public int order() {
        return 26;
    }

    @Override
    public GuardResult check(PricingSignalSet signals, BigDecimal targetPrice,
                             GuardConfig config) {
        if (!config.isCompetitorTrustGuardEnabled()) {
            return GuardResult.pass(guardName());
        }

        if (signals.competitorTrustLevel() == null) {
            return GuardResult.pass(guardName());
        }

        if ("CANDIDATE".equals(signals.competitorTrustLevel())) {
            return GuardResult.block(guardName(),
                    MessageCodes.PRICING_COMPETITOR_UNTRUSTED,
                    Map.of("trustLevel", signals.competitorTrustLevel()));
        }

        return GuardResult.pass(guardName());
    }
}
