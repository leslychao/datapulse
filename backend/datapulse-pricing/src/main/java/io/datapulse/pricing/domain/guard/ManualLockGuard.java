package io.datapulse.pricing.domain.guard;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import io.datapulse.common.error.MessageCodes;
import io.datapulse.pricing.domain.GuardConfig;
import io.datapulse.pricing.domain.GuardResult;
import io.datapulse.pricing.domain.PricingSignalSet;

@Component
public class ManualLockGuard implements PricingGuard {

    @Override
    public String guardName() {
        return "manual_lock_guard";
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public GuardResult check(PricingSignalSet signals, BigDecimal targetPrice, GuardConfig config) {
        if (signals.manualLockActive()) {
            return GuardResult.block(guardName(), MessageCodes.PRICING_GUARD_MANUAL_LOCK);
        }
        return GuardResult.pass(guardName());
    }
}
