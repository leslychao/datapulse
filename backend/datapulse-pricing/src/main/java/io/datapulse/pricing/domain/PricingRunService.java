package io.datapulse.pricing.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.datapulse.common.error.MessageCodes;
import io.datapulse.platform.audit.AutomationBlockerChecker;
import io.datapulse.pricing.domain.PricingConstraintResolver.ConstraintResolution;
import io.datapulse.pricing.domain.guard.PricingGuardChain;
import io.datapulse.pricing.domain.guard.PricingGuardChain.GuardChainResult;
import io.datapulse.pricing.domain.strategy.PricingStrategyRegistry;
import io.datapulse.pricing.persistence.PriceDecisionEntity;
import io.datapulse.pricing.persistence.PriceDecisionRepository;
import io.datapulse.pricing.persistence.PricePolicyEntity;
import io.datapulse.pricing.persistence.PricingDataReadRepository;
import io.datapulse.pricing.persistence.PricingDataReadRepository.OfferRow;
import io.datapulse.pricing.persistence.PricingRunEntity;
import io.datapulse.pricing.persistence.PricingRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Orchestrates a complete pricing run for a marketplace connection.
 * <p>
 * Pipeline per connection:
 * 1. Load all marketplace_offers for connection
 * 2. Resolve effective policy per offer (specificity + priority)
 * 3. Filter eligible offers
 * 4. Batch collect signals
 * 5. Per-offer: strategy → constraints → guards → decision → explanation
 * 6. Batch save decisions
 * 7. Schedule actions for CHANGE decisions (SEMI_AUTO / FULL_AUTO / SIMULATED)
 * 8. Update pricing_run counters and status
 * 9. Publish PricingRunCompletedEvent
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PricingRunService {

    private final AutomationBlockerChecker automationBlockerChecker;
    private final PricingDataReadRepository dataReadRepository;
    private final PolicyResolver policyResolver;
    private final PricingSignalCollector signalCollector;
    private final PricingStrategyRegistry strategyRegistry;
    private final PricingConstraintResolver constraintResolver;
    private final PricingGuardChain guardChain;
    private final ExplanationBuilder explanationBuilder;
    private final PricingActionScheduler actionScheduler;
    private final PriceDecisionRepository decisionRepository;
    private final PricingRunRepository runRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void executeRun(long pricingRunId) {
        PricingRunEntity run = runRepository.findById(pricingRunId)
                .orElseThrow(() -> new IllegalStateException(
                        "PricingRun not found: " + pricingRunId));

        if (run.getStatus() != RunStatus.PENDING) {
            log.warn("PricingRun {} is not PENDING (status={}), skipping", pricingRunId, run.getStatus());
            return;
        }

        run.setStatus(RunStatus.IN_PROGRESS);
        run.setStartedAt(OffsetDateTime.now());
        runRepository.save(run);

        try {
            RunCounters counters = processConnection(run);
            completeRun(run, counters, null);
        } catch (Exception e) {
            log.error("PricingRun {} failed: {}", pricingRunId, e.getMessage(), e);
            failRun(run, e);
        }
    }

    private RunCounters processConnection(PricingRunEntity run) {
        if (automationBlockerChecker.isBlocked(run.getWorkspaceId(), run.getConnectionId())) {
            log.warn("PricingRun {}: automation blocked for connection {} (blocking alert exists)",
                    run.getId(), run.getConnectionId());
            run.setErrorDetails(serializeJson(Map.of(
                    "reason", MessageCodes.PRICING_AUTOMATION_BLOCKED,
                    "connectionId", run.getConnectionId())));
            return RunCounters.EMPTY;
        }

        List<OfferRow> allOffers = dataReadRepository.findOffersByConnection(run.getConnectionId());
        run.setTotalOffers(allOffers.size());

        if (allOffers.isEmpty()) {
            log.info("PricingRun {}: no offers found for connection {}", run.getId(), run.getConnectionId());
            return RunCounters.EMPTY;
        }

        Map<Long, PricePolicyEntity> effectivePolicies = policyResolver.resolveEffectivePolicies(
                run.getWorkspaceId(), run.getConnectionId(), allOffers);

        List<EligibleOffer> eligible = filterEligible(allOffers, effectivePolicies);
        run.setEligibleCount(eligible.size());

        if (eligible.isEmpty()) {
            log.info("PricingRun {}: no eligible offers", run.getId());
            return RunCounters.EMPTY;
        }

        List<Long> eligibleOfferIds = eligible.stream()
                .map(EligibleOffer::marketplaceOfferId)
                .toList();

        int volatilityDays = resolveMaxVolatilityDays(effectivePolicies);
        Map<Long, PricingSignalSet> signals = signalCollector.collectBatch(
                eligibleOfferIds, run.getConnectionId(), volatilityDays);

        return processOffers(run, eligible, signals);
    }

    private RunCounters processOffers(PricingRunEntity run, List<EligibleOffer> eligible,
                                      Map<Long, PricingSignalSet> signals) {
        int changeCount = 0;
        int skipCount = 0;
        int holdCount = 0;
        List<PriceDecisionEntity> decisions = new ArrayList<>();

        for (EligibleOffer offer : eligible) {
            PricingSignalSet signalSet = signals.get(offer.marketplaceOfferId());
            if (signalSet == null) {
                skipCount++;
                continue;
            }

            PricePolicyEntity policy = offer.policy();
            PolicySnapshot snapshot = buildSnapshot(policy);
            GuardConfig guardConfig = parseGuardConfig(policy.getGuardConfig())
                    .withMinMarginPct(policy.getMinMarginPct());

            PriceDecisionEntity decision = processSingleOffer(
                    run, offer, signalSet, policy, snapshot, guardConfig);
            decisions.add(decision);

            switch (decision.getDecisionType()) {
                case CHANGE -> changeCount++;
                case SKIP -> skipCount++;
                case HOLD -> holdCount++;
            }
        }

        decisionRepository.saveAll(decisions);

        scheduleActions(decisions, run.getWorkspaceId());

        return new RunCounters(changeCount, skipCount, holdCount);
    }

    private PriceDecisionEntity processSingleOffer(PricingRunEntity run, EligibleOffer offer,
                                                    PricingSignalSet signals, PricePolicyEntity policy,
                                                    PolicySnapshot snapshot, GuardConfig guardConfig) {
        StrategyResult strategyResult = strategyRegistry.resolve(policy.getStrategyType())
                .calculate(signals, snapshot);

        if (strategyResult.rawTargetPrice() == null) {
            String skipReasonKey = strategyResult.reasonKey();
            return buildDecision(run, offer, policy, snapshot, signals,
                    DecisionType.HOLD, null, null, strategyResult,
                    List.of(), List.of(),
                    explanationBuilder.buildHold(strategyResult.explanation(), snapshot),
                    skipReasonKey);
        }

        ConstraintResolution constrained = constraintResolver.resolve(
                strategyResult.rawTargetPrice(), signals, snapshot);

        GuardChainResult guardResult = guardChain.evaluate(signals, constrained.clampedPrice(), guardConfig);

        if (!guardResult.allPassed()) {
            String skipReasonKey = guardResult.blockingGuard().reason();
            return buildDecision(run, offer, policy, snapshot, signals,
                    DecisionType.SKIP, constrained.clampedPrice(), strategyResult.rawTargetPrice(),
                    strategyResult, constrained.applied(), guardResult.evaluations(),
                    explanationBuilder.buildSkipGuard(snapshot, guardResult.blockingGuard()),
                    skipReasonKey);
        }

        BigDecimal targetPrice = constrained.clampedPrice();
        boolean noChange = signals.currentPrice() != null
                && targetPrice.compareTo(signals.currentPrice()) == 0;

        if (noChange) {
            return buildDecision(run, offer, policy, snapshot, signals,
                    DecisionType.SKIP, targetPrice, strategyResult.rawTargetPrice(),
                    strategyResult, constrained.applied(), guardResult.evaluations(),
                    explanationBuilder.buildSkip(MessageCodes.PRICING_NO_CHANGE, null, null),
                    MessageCodes.PRICING_NO_CHANGE);
        }

        String actionStatus = resolveActionStatus(policy.getExecutionMode());
        String explanation = explanationBuilder.buildChange(
                signals.currentPrice(), targetPrice, snapshot,
                strategyResult.explanation(), constrained.applied(),
                policy.getExecutionMode(), actionStatus);

        return buildDecision(run, offer, policy, snapshot, signals,
                DecisionType.CHANGE, targetPrice, strategyResult.rawTargetPrice(),
                strategyResult, constrained.applied(), guardResult.evaluations(), explanation,
                null);
    }

    private List<EligibleOffer> filterEligible(List<OfferRow> offers,
                                                Map<Long, PricePolicyEntity> policies) {
        List<EligibleOffer> eligible = new ArrayList<>();
        for (OfferRow offer : offers) {
            PricePolicyEntity policy = policies.get(offer.id());
            if (policy == null) {
                continue;
            }
            if (!"ACTIVE".equalsIgnoreCase(offer.status())
                    && !"active".equalsIgnoreCase(offer.status())) {
                continue;
            }
            eligible.add(new EligibleOffer(
                    offer.id(), offer.sellerSkuId(), offer.categoryId(),
                    offer.connectionId(), policy));
        }
        return eligible;
    }

    private void scheduleActions(List<PriceDecisionEntity> decisions, long workspaceId) {
        for (PriceDecisionEntity decision : decisions) {
            if (decision.getDecisionType() != DecisionType.CHANGE) {
                continue;
            }

            ExecutionMode mode = ExecutionMode.valueOf(
                    decision.getExecutionMode().equals("SIMULATED") ? "SIMULATED"
                            : mapToExecutionMode(decision));

            if (mode == ExecutionMode.RECOMMENDATION) {
                continue;
            }

            actionScheduler.scheduleAction(
                    decision.getId(),
                    decision.getMarketplaceOfferId(),
                    decision.getTargetPrice(),
                    mode,
                    workspaceId);
        }
    }

    private String mapToExecutionMode(PriceDecisionEntity decision) {
        try {
            PolicySnapshot snap = objectMapper.readValue(
                    decision.getPolicySnapshot(), PolicySnapshot.class);
            return snap.executionMode().name();
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse policy snapshot for decision {}, defaulting to RECOMMENDATION",
                    decision.getId());
            return ExecutionMode.RECOMMENDATION.name();
        }
    }

    private void completeRun(PricingRunEntity run, RunCounters counters, Exception partialError) {
        run.setChangeCount(counters.changeCount);
        run.setSkipCount(counters.skipCount);
        run.setHoldCount(counters.holdCount);
        run.setCompletedAt(OffsetDateTime.now());

        if (partialError != null) {
            run.setStatus(RunStatus.COMPLETED_WITH_ERRORS);
            run.setErrorDetails(serializeError(partialError));
        } else {
            run.setStatus(RunStatus.COMPLETED);
        }
        runRepository.save(run);

        eventPublisher.publishEvent(new PricingRunCompletedEvent(
                run.getId(), run.getWorkspaceId(), run.getConnectionId(),
                counters.changeCount, counters.skipCount, counters.holdCount,
                run.getStatus()));
    }

    private void failRun(PricingRunEntity run, Exception error) {
        run.setStatus(RunStatus.FAILED);
        run.setCompletedAt(OffsetDateTime.now());
        run.setErrorDetails(serializeError(error));
        runRepository.save(run);

        eventPublisher.publishEvent(new PricingRunCompletedEvent(
                run.getId(), run.getWorkspaceId(), run.getConnectionId(),
                0, 0, 0, RunStatus.FAILED));
    }

    private PriceDecisionEntity buildDecision(PricingRunEntity run, EligibleOffer offer,
                                               PricePolicyEntity policy, PolicySnapshot snapshot,
                                               PricingSignalSet signals,
                                               DecisionType decisionType,
                                               BigDecimal targetPrice, BigDecimal rawPrice,
                                               StrategyResult strategyResult,
                                               List<ConstraintRecord> constraints,
                                               List<GuardEvaluationRecord> guards,
                                               String explanation,
                                               String skipReasonKey) {
        var entity = new PriceDecisionEntity();
        entity.setWorkspaceId(run.getWorkspaceId());
        entity.setPricingRunId(run.getId());
        entity.setMarketplaceOfferId(offer.marketplaceOfferId());
        entity.setPricePolicyId(policy.getId());
        entity.setPolicyVersion(policy.getVersion());
        entity.setPolicySnapshot(serializeJson(snapshot));
        entity.setDecisionType(decisionType);
        entity.setCurrentPrice(signals.currentPrice());
        entity.setTargetPrice(targetPrice);
        entity.setStrategyType(policy.getStrategyType());
        entity.setStrategyRawPrice(rawPrice != null ? rawPrice : strategyResult.rawTargetPrice());
        entity.setSignalSnapshot(serializeJson(signals));
        entity.setConstraintsApplied(serializeJson(constraints));
        entity.setGuardsEvaluated(serializeJson(guards));
        entity.setExplanationSummary(explanation);

        if (targetPrice != null && signals.currentPrice() != null) {
            BigDecimal changeAmount = targetPrice.subtract(signals.currentPrice());
            entity.setPriceChangeAmount(changeAmount);
            if (signals.currentPrice().compareTo(BigDecimal.ZERO) != 0) {
                entity.setPriceChangePct(changeAmount
                        .divide(signals.currentPrice(), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)));
            }
        }

        String decisionExecMode = policy.getExecutionMode() == ExecutionMode.SIMULATED
                ? "SIMULATED" : "LIVE";
        entity.setExecutionMode(decisionExecMode);

        if (skipReasonKey != null) {
            entity.setSkipReason(skipReasonKey);
        }

        return entity;
    }

    private PolicySnapshot buildSnapshot(PricePolicyEntity policy) {
        return new PolicySnapshot(
                policy.getId(),
                policy.getVersion(),
                policy.getName(),
                policy.getStrategyType(),
                policy.getStrategyParams(),
                policy.getMinMarginPct(),
                policy.getMaxPriceChangePct(),
                policy.getMinPrice(),
                policy.getMaxPrice(),
                policy.getGuardConfig(),
                policy.getExecutionMode());
    }

    private GuardConfig parseGuardConfig(String json) {
        if (json == null || json.isBlank()) {
            return GuardConfig.DEFAULTS;
        }
        try {
            return objectMapper.readValue(json, GuardConfig.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse guard_config, using defaults: {}", e.getMessage());
            return GuardConfig.DEFAULTS;
        }
    }

    private int resolveMaxVolatilityDays(Map<Long, PricePolicyEntity> policies) {
        return policies.values().stream()
                .map(p -> parseGuardConfig(p.getGuardConfig()))
                .mapToInt(GuardConfig::effectiveVolatilityPeriodDays)
                .max()
                .orElse(7);
    }

    private String resolveActionStatus(ExecutionMode mode) {
        return switch (mode) {
            case SEMI_AUTO -> "PENDING_APPROVAL";
            case FULL_AUTO, SIMULATED -> "APPROVED";
            case RECOMMENDATION -> "RECOMMENDATION";
        };
    }

    private String serializeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.error("JSON serialization failed", e);
            return "{}";
        }
    }

    private String serializeError(Exception error) {
        return serializeJson(Map.of(
                "type", error.getClass().getSimpleName(),
                "message", error.getMessage() != null ? error.getMessage() : "Unknown error"));
    }

    private record RunCounters(int changeCount, int skipCount, int holdCount) {
        static final RunCounters EMPTY = new RunCounters(0, 0, 0);
    }
}
