package io.datapulse.pricing.domain.strategy;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.datapulse.common.error.MessageCodes;
import io.datapulse.pricing.domain.PolicySnapshot;
import io.datapulse.pricing.domain.PolicyType;
import io.datapulse.pricing.domain.PriceCorridorParams;
import io.datapulse.pricing.domain.PricingSignalSet;
import io.datapulse.pricing.domain.StrategyResult;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PriceCorridorStrategy implements PricingStrategy {

    private final ObjectMapper objectMapper;

    @Override
    public PolicyType strategyType() {
        return PolicyType.PRICE_CORRIDOR;
    }

    @Override
    public StrategyResult calculate(PricingSignalSet signals, PolicySnapshot policy) {
        if (signals.currentPrice() == null) {
            return StrategyResult.skip("Current price not available",
                    MessageCodes.PRICING_CURRENT_PRICE_MISSING);
        }

        PriceCorridorParams params = parseParams(policy.strategyParams());
        BigDecimal current = signals.currentPrice();

        if (params.minPrice() != null && current.compareTo(params.minPrice()) < 0) {
            return StrategyResult.success(params.minPrice(),
                    "current %s < corridor min %s → target=%s"
                            .formatted(current.toPlainString(),
                                    params.minPrice().toPlainString(),
                                    params.minPrice().toPlainString()));
        }

        if (params.maxPrice() != null && current.compareTo(params.maxPrice()) > 0) {
            return StrategyResult.success(params.maxPrice(),
                    "current %s > corridor max %s → target=%s"
                            .formatted(current.toPlainString(),
                                    params.maxPrice().toPlainString(),
                                    params.maxPrice().toPlainString()));
        }

        return StrategyResult.success(current,
                "current %s within corridor → no change".formatted(current.toPlainString()));
    }

    private PriceCorridorParams parseParams(String json) {
        try {
            return objectMapper.readValue(json, PriceCorridorParams.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid PRICE_CORRIDOR strategy_params JSON", e);
        }
    }
}
