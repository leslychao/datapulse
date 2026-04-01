package io.datapulse.pricing.domain.strategy;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import io.datapulse.pricing.domain.PolicyType;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class PricingStrategyRegistry {

    private final Map<PolicyType, PricingStrategy> strategies;

    public PricingStrategyRegistry(List<PricingStrategy> strategyList) {
        this.strategies = new EnumMap<>(PolicyType.class);
        for (PricingStrategy strategy : strategyList) {
            PricingStrategy existing = strategies.put(strategy.strategyType(), strategy);
            if (existing != null) {
                throw new IllegalStateException(
                        "Duplicate PricingStrategy for type %s: %s vs %s"
                                .formatted(strategy.strategyType(),
                                        existing.getClass().getSimpleName(),
                                        strategy.getClass().getSimpleName()));
            }
            log.info("Registered pricing strategy: type={}, class={}",
                    strategy.strategyType(), strategy.getClass().getSimpleName());
        }
    }

    public PricingStrategy resolve(PolicyType type) {
        PricingStrategy strategy = strategies.get(type);
        if (strategy == null) {
            throw new IllegalArgumentException("No PricingStrategy registered for type: " + type);
        }
        return strategy;
    }
}
