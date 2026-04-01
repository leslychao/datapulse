package io.datapulse.pricing.domain.guard;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

import org.springframework.stereotype.Component;

import io.datapulse.common.error.MessageCodes;
import io.datapulse.pricing.domain.GuardConfig;
import io.datapulse.pricing.domain.GuardResult;
import io.datapulse.pricing.domain.PricingSignalSet;

@Component
public class MarginGuard implements PricingGuard {

    @Override
    public String guardName() {
        return "margin_guard";
    }

    @Override
    public int order() {
        return 40;
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

        BigDecimal margin = targetPrice.subtract(signals.cogs())
                .divide(targetPrice, 4, RoundingMode.HALF_UP);

        if (margin.compareTo(BigDecimal.ZERO) < 0) {
            BigDecimal marginPct = margin.multiply(BigDecimal.valueOf(100))
                    .setScale(1, RoundingMode.HALF_UP);
            return GuardResult.block(guardName(), MessageCodes.PRICING_GUARD_MARGIN_NEGATIVE,
                    Map.of("marginPct", marginPct.toPlainString(),
                            "targetPrice", targetPrice.toPlainString(),
                            "cogs", signals.cogs().toPlainString()));
        }

        return GuardResult.pass(guardName());
    }
}
