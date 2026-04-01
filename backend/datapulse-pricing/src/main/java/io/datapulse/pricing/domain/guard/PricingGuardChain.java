package io.datapulse.pricing.domain.guard;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import io.datapulse.pricing.domain.GuardConfig;
import io.datapulse.pricing.domain.GuardEvaluationRecord;
import io.datapulse.pricing.domain.GuardResult;
import io.datapulse.pricing.domain.PricingSignalSet;

/**
 * Runs all registered guards in order (cheapest first).
 * Short-circuits on the first blocking guard.
 */
@Service
public class PricingGuardChain {

    private final List<PricingGuard> orderedGuards;

    public PricingGuardChain(List<PricingGuard> guards) {
        this.orderedGuards = guards.stream()
                .sorted(Comparator.comparingInt(PricingGuard::order))
                .toList();
    }

    public GuardChainResult evaluate(PricingSignalSet signals, BigDecimal targetPrice,
                                     GuardConfig config) {
        List<GuardEvaluationRecord> evaluations = new ArrayList<>();

        for (PricingGuard guard : orderedGuards) {
            GuardResult result = guard.check(signals, targetPrice, config);
            evaluations.add(new GuardEvaluationRecord(
                    result.guardName(), result.passed(), result.reason(), result.args()));

            if (!result.passed()) {
                return new GuardChainResult(false, result, evaluations);
            }
        }

        return new GuardChainResult(true, null, evaluations);
    }

    public record GuardChainResult(
            boolean allPassed,
            GuardResult blockingGuard,
            List<GuardEvaluationRecord> evaluations
    ) {
    }
}
