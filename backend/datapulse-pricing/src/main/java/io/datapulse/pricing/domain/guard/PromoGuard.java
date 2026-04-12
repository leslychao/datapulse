package io.datapulse.pricing.domain.guard;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import io.datapulse.common.error.MessageCodes;
import io.datapulse.pricing.domain.GuardConfig;
import io.datapulse.pricing.domain.GuardResult;
import io.datapulse.pricing.domain.PricingSignalSet;

@Component
public class PromoGuard implements PricingGuard {

    @Override
    public String guardName() {
        return "promo_guard";
    }

    @Override
    public int order() {
        return 11;
    }

    @Override
    public GuardResult check(PricingSignalSet signals, BigDecimal targetPrice,
                             GuardConfig config) {
        if (!config.isPromoGuardEnabled()) {
            return GuardResult.pass(guardName());
        }

        if (signals.promoActive()) {
            return GuardResult.block(guardName(), MessageCodes.PRICING_GUARD_PROMO_ACTIVE);
        }
        return GuardResult.pass(guardName());
    }
}
