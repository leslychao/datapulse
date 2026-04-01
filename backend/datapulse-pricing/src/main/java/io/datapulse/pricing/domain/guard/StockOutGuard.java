package io.datapulse.pricing.domain.guard;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import io.datapulse.common.error.MessageCodes;
import io.datapulse.pricing.domain.GuardConfig;
import io.datapulse.pricing.domain.GuardResult;
import io.datapulse.pricing.domain.PricingSignalSet;

@Component
public class StockOutGuard implements PricingGuard {

    @Override
    public String guardName() {
        return "stock_out_guard";
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    public GuardResult check(PricingSignalSet signals, BigDecimal targetPrice, GuardConfig config) {
        if (!config.isStockOutGuardEnabled()) {
            return GuardResult.pass(guardName());
        }

        if (signals.availableStock() != null && signals.availableStock() == 0) {
            return GuardResult.block(guardName(), MessageCodes.PRICING_GUARD_STOCK_OUT);
        }
        return GuardResult.pass(guardName());
    }
}
