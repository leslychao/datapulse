package io.datapulse.pricing.domain.strategy;

import io.datapulse.pricing.domain.PolicySnapshot;
import io.datapulse.pricing.domain.PolicyType;
import io.datapulse.pricing.domain.PricingSignalSet;
import io.datapulse.pricing.domain.StrategyResult;

public interface PricingStrategy {

    PolicyType strategyType();

    StrategyResult calculate(PricingSignalSet signals, PolicySnapshot policy);
}
