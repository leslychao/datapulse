package io.datapulse.pricing.domain.guard;

import io.datapulse.pricing.domain.GuardConfig;
import io.datapulse.pricing.domain.GuardResult;
import io.datapulse.pricing.domain.PricingSignalSet;

import java.math.BigDecimal;

public interface PricingGuard {

    String guardName();

    int order();

    GuardResult check(PricingSignalSet signals, BigDecimal targetPrice, GuardConfig config);
}
