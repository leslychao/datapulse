package io.datapulse.pricing.domain.strategy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.datapulse.common.error.MessageCodes;
import io.datapulse.pricing.domain.CompositeParams;
import io.datapulse.pricing.domain.CompositeParams.ComponentConfig;
import io.datapulse.pricing.domain.PolicySnapshot;
import io.datapulse.pricing.domain.PolicyType;
import io.datapulse.pricing.domain.PricingSignalSet;
import io.datapulse.pricing.domain.StrategyResult;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CompositeStrategy implements PricingStrategy {

    private final PricingStrategyRegistry registry;
    private final ObjectMapper objectMapper;

    @Override
    public PolicyType strategyType() {
        return PolicyType.COMPOSITE;
    }

    @Override
    public StrategyResult calculate(PricingSignalSet signals, PolicySnapshot policy) {
        CompositeParams params = parseParams(policy.strategyParams());

        List<ComponentResult> results = evaluateComponents(params, signals, policy);

        List<ComponentResult> successful = results.stream()
            .filter(r -> r.result.rawTargetPrice() != null)
            .toList();

        if (successful.isEmpty()) {
            String explanation = buildAllSkippedExplanation(results);
            return StrategyResult.skip(explanation,
                MessageCodes.PRICING_COMPOSITE_ALL_SKIPPED);
        }

        BigDecimal totalWeight = successful.stream()
            .map(r -> r.weight)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal weightedTarget = BigDecimal.ZERO;
        for (ComponentResult comp : successful) {
            BigDecimal effectiveWeight = comp.weight
                .divide(totalWeight, 6, RoundingMode.HALF_UP);
            weightedTarget = weightedTarget.add(
                comp.result.rawTargetPrice().multiply(effectiveWeight));
        }
        weightedTarget = weightedTarget.setScale(2, RoundingMode.HALF_UP);

        String explanation = buildExplanation(results, successful.size(),
            results.size(), weightedTarget);

        return StrategyResult.success(weightedTarget, explanation);
    }

    private List<ComponentResult> evaluateComponents(
        CompositeParams params, PricingSignalSet signals,
        PolicySnapshot parentPolicy) {

        List<ComponentResult> results = new ArrayList<>();

        for (ComponentConfig comp : params.components()) {
            PricingStrategy strategy = registry.resolve(comp.strategyType());

            PolicySnapshot componentSnapshot = new PolicySnapshot(
                parentPolicy.policyId(),
                parentPolicy.version(),
                parentPolicy.name(),
                comp.strategyType(),
                comp.strategyParams(),
                parentPolicy.minMarginPct(),
                parentPolicy.maxPriceChangePct(),
                parentPolicy.minPrice(),
                parentPolicy.maxPrice(),
                parentPolicy.guardConfig(),
                parentPolicy.executionMode());

            StrategyResult result = strategy.calculate(signals, componentSnapshot);
            results.add(new ComponentResult(comp.strategyType(), comp.weight(), result));
        }

        return results;
    }

    private String buildExplanation(List<ComponentResult> results,
                                    int successCount, int totalCount,
                                    BigDecimal weightedTarget) {
        var sb = new StringBuilder();
        sb.append("COMPOSITE (%d/%d components):\n".formatted(successCount, totalCount));

        for (ComponentResult comp : results) {
            if (comp.result.rawTargetPrice() != null) {
                sb.append(String.format(Locale.US,
                    "  %s w=%.2f → %s (%s)\n",
                    comp.type.name(),
                    comp.weight,
                    comp.result.rawTargetPrice().toPlainString(),
                    comp.result.explanation()));
            } else {
                sb.append("  %s SKIPPED (%s)\n".formatted(
                    comp.type.name(),
                    comp.result.explanation()));
            }
        }

        sb.append("weighted_raw=%s".formatted(weightedTarget.toPlainString()));
        return sb.toString();
    }

    private String buildAllSkippedExplanation(List<ComponentResult> results) {
        var sb = new StringBuilder();
        sb.append("COMPOSITE (0/%d components):\n".formatted(results.size()));

        for (ComponentResult comp : results) {
            sb.append("  %s SKIPPED (%s)\n".formatted(
                comp.type.name(),
                comp.result.explanation()));
        }

        sb.append("All component strategies skipped");
        return sb.toString();
    }

    private CompositeParams parseParams(String json) {
        try {
            return objectMapper.readValue(json, CompositeParams.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                "Invalid COMPOSITE strategy_params JSON", e);
        }
    }

    private record ComponentResult(
        PolicyType type,
        BigDecimal weight,
        StrategyResult result
    ) {}
}
