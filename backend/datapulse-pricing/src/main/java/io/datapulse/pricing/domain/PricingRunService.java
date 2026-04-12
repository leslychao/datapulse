package io.datapulse.pricing.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

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

    private static final int BATCH_SIZE = 100;

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
    private final BlastRadiusBreaker blastRadiusBreaker;
    private final FullAutoSafetyGate fullAutoSafetyGate;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;

    public void executeRun(long pricingRunId) {
        PricingRunEntity run = transactionTemplate.execute(status -> {
            PricingRunEntity r = runRepository.findById(pricingRunId)
                    .orElseThrow(() -> new IllegalStateException(
                            "PricingRun not found: " + pricingRunId));
            if (r.getStatus() != RunStatus.PENDING) {
                log.warn("PricingRun {} is not PENDING (status={}), skipping",
                        pricingRunId, r.getStatus());
                return null;
            }
            r.setStatus(RunStatus.IN_PROGRESS);
            r.setStartedAt(OffsetDateTime.now());
            return runRepository.save(r);
        });

        if (run == null) {
            return;
        }

        final long runId = run.getId();
        final long connectionId = run.getConnectionId();
        final long workspaceId = run.getWorkspaceId();

        try {
            RunResult result = processConnection(runId, connectionId, workspaceId);
            transactionTemplate.executeWithoutResult(status -> {
                PricingRunEntity r = runRepository.findById(runId).orElseThrow();
                r.setTotalOffers(result.totalOffers);
                r.setEligibleCount(result.eligibleCount);
                completeRun(r, result.counters, result.partialError);
            });
        } catch (Exception e) {
            log.error("PricingRun {} failed: {}", runId, e.getMessage(), e);
            transactionTemplate.executeWithoutResult(status -> {
                PricingRunEntity r = runRepository.findById(runId).orElseThrow();
                failRun(r, e);
            });
        }
    }

    private RunResult processConnection(long runId, long connectionId, long workspaceId) {
        if (automationBlockerChecker.isBlocked(workspaceId, connectionId)) {
            log.warn("PricingRun {}: automation blocked for connection {} (blocking alert exists)",
                    runId, connectionId);
            transactionTemplate.executeWithoutResult(status -> {
                PricingRunEntity r = runRepository.findById(runId).orElseThrow();
                r.setErrorDetails(serializeJson(Map.of(
                        "reason", MessageCodes.PRICING_AUTOMATION_BLOCKED,
                        "connectionId", connectionId)));
                runRepository.save(r);
            });
            return RunResult.empty(0, 0);
        }

        List<OfferRow> allOffers = dataReadRepository.findOffersByConnection(connectionId);

        if (allOffers.isEmpty()) {
            log.info("PricingRun {}: no offers found for connection {}", runId, connectionId);
            return RunResult.empty(0, 0);
        }

        Map<Long, PricePolicyEntity> effectivePolicies = policyResolver.resolveEffectivePolicies(
                workspaceId, connectionId, allOffers);

        List<EligibleOffer> eligible = filterEligible(allOffers, effectivePolicies);

        if (eligible.isEmpty()) {
            log.info("PricingRun {}: no eligible offers", runId);
            return RunResult.empty(allOffers.size(), 0);
        }

        int volatilityDays = resolveMaxVolatilityDays(effectivePolicies);

        boolean isFullAuto = isFullAutoRun(eligible);
        if (isFullAuto) {
            blastRadiusBreaker.reset();
        }
        Set<Long> downgradedPolicyIds = checkFullAutoSafety(eligible);

        RunCounters totalCounters = RunCounters.EMPTY;
        Exception partialError = null;
        boolean paused = false;

        for (int from = 0; from < eligible.size(); from += BATCH_SIZE) {
            int to = Math.min(from + BATCH_SIZE, eligible.size());
            List<EligibleOffer> chunk = eligible.subList(from, to);

            try {
                BatchResult batch = processChunk(
                        runId, workspaceId, connectionId, chunk,
                        volatilityDays, isFullAuto, downgradedPolicyIds);
                totalCounters = totalCounters.add(batch.counters);

                if (batch.paused) {
                    paused = true;
                    break;
                }
            } catch (Exception e) {
                log.error("PricingRun {}: batch [{}-{}) failed: {}",
                        runId, from, to, e.getMessage(), e);
                partialError = e;
            }
        }

        if (paused) {
            transactionTemplate.executeWithoutResult(status -> {
                PricingRunEntity r = runRepository.findById(runId).orElseThrow();
                r.setTotalOffers(allOffers.size());
                r.setEligibleCount(eligible.size());
                String metric = blastRadiusBreaker.breachedMetric();
                BigDecimal value = "max_abs_change_pct".equals(metric)
                        ? blastRadiusBreaker.currentMaxAbsChangePct()
                        : blastRadiusBreaker.currentRatio();
                pauseRunForBlastRadius(r, metric, value);
            });
        }

        return new RunResult(
                allOffers.size(), eligible.size(), totalCounters, partialError);
    }

    private BatchResult processChunk(long runId, long workspaceId, long connectionId,
                                     List<EligibleOffer> chunk, int volatilityDays,
                                     boolean isFullAuto, Set<Long> downgradedPolicyIds) {
        List<Long> chunkOfferIds = chunk.stream()
                .map(EligibleOffer::marketplaceOfferId)
                .toList();

        Map<Long, PricingSignalSet> signals = signalCollector.collectBatch(
                chunkOfferIds, connectionId, volatilityDays);

        return transactionTemplate.execute(status -> {
            int changeCount = 0;
            int skipCount = 0;
            int holdCount = 0;
            List<PriceDecisionEntity> decisions = new ArrayList<>();

            for (EligibleOffer offer : chunk) {
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
                        runId, workspaceId, offer, signalSet,
                        policy, snapshot, guardConfig);
                decisions.add(decision);

                switch (decision.getDecisionType()) {
                    case CHANGE -> changeCount++;
                    case SKIP -> skipCount++;
                    case HOLD -> holdCount++;
                }

                if (isFullAuto && decision.getDecisionType() == DecisionType.CHANGE) {
                    blastRadiusBreaker.recordDecision(
                            decision.getCurrentPrice(), decision.getTargetPrice());
                    if (blastRadiusBreaker.isBreached()) {
                        log.warn("Blast radius breached for run {}: chunk saved", runId);
                        decisionRepository.saveAll(decisions);
                        return new BatchResult(
                                new RunCounters(changeCount, skipCount, holdCount), true);
                    }
                }
            }

            decisionRepository.saveAll(decisions);

            Map<Long, PricePolicyEntity> chunkPolicyById = new HashMap<>();
            for (EligibleOffer o : chunk) {
                chunkPolicyById.putIfAbsent(o.policy().getId(), o.policy());
            }
            scheduleActions(decisions, workspaceId, connectionId,
                    chunkPolicyById, downgradedPolicyIds);

            return new BatchResult(new RunCounters(changeCount, skipCount, holdCount), false);
        });
    }

    private Set<Long> checkFullAutoSafety(List<EligibleOffer> eligible) {
        Set<Long> downgraded = new HashSet<>();
        Set<Long> checked = new HashSet<>();
        for (EligibleOffer offer : eligible) {
            PricePolicyEntity policy = offer.policy();
            if (policy.getExecutionMode() != ExecutionMode.FULL_AUTO
                    || !checked.add(policy.getId())) {
                continue;
            }
            List<String> violations = fullAutoSafetyGate.runtimeCheck(policy);
            if (!violations.isEmpty()) {
                log.warn("FULL_AUTO runtime check failed for policy {}: {}, "
                        + "downgrading to SEMI_AUTO for this run",
                        policy.getId(), violations);
                downgraded.add(policy.getId());
            }
        }
        return downgraded;
    }

    private boolean isFullAutoRun(List<EligibleOffer> eligible) {
        return eligible.stream()
                .anyMatch(o -> o.policy().getExecutionMode() == ExecutionMode.FULL_AUTO);
    }

    private void pauseRunForBlastRadius(PricingRunEntity run, String metric, BigDecimal value) {
        run.setStatus(RunStatus.PAUSED);
        run.setErrorDetails(serializeJson(Map.of(
                "reason", MessageCodes.PRICING_RUN_BLAST_RADIUS_BREACHED,
                "metric", metric,
                "value", value.toPlainString())));
        runRepository.save(run);
    }

    private PriceDecisionEntity processSingleOffer(long runId, long workspaceId,
                                                    EligibleOffer offer,
                                                    PricingSignalSet signals,
                                                    PricePolicyEntity policy,
                                                    PolicySnapshot snapshot,
                                                    GuardConfig guardConfig) {
        StrategyResult strategyResult = strategyRegistry.resolve(policy.getStrategyType())
                .calculate(signals, snapshot);

        if (strategyResult.rawTargetPrice() == null) {
            String skipReasonKey = strategyResult.reasonKey();
            return buildDecision(runId, workspaceId, offer, policy, snapshot, signals,
                    DecisionType.HOLD, null, null, strategyResult,
                    List.of(), List.of(),
                    explanationBuilder.buildHold(strategyResult.explanation(), snapshot),
                    skipReasonKey);
        }

        ConstraintResolution constrained = constraintResolver.resolve(
                strategyResult.rawTargetPrice(), signals, snapshot);

        GuardChainResult guardResult = guardChain.evaluate(
                signals, constrained.clampedPrice(), guardConfig);

        if (!guardResult.allPassed()) {
            String skipReasonKey = guardResult.blockingGuard().reason();
            return buildDecision(runId, workspaceId, offer, policy, snapshot, signals,
                    DecisionType.SKIP, constrained.clampedPrice(),
                    strategyResult.rawTargetPrice(),
                    strategyResult, constrained.applied(), guardResult.evaluations(),
                    explanationBuilder.buildSkipGuard(snapshot, guardResult.blockingGuard()),
                    skipReasonKey);
        }

        BigDecimal targetPrice = constrained.clampedPrice();
        boolean noChange = signals.currentPrice() != null
                && targetPrice.compareTo(signals.currentPrice()) == 0;

        if (noChange) {
            return buildDecision(runId, workspaceId, offer, policy, snapshot, signals,
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

        return buildDecision(runId, workspaceId, offer, policy, snapshot, signals,
                DecisionType.CHANGE, targetPrice, strategyResult.rawTargetPrice(),
                strategyResult, constrained.applied(), guardResult.evaluations(),
                explanation, null);
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

    private void scheduleActions(List<PriceDecisionEntity> decisions, long workspaceId,
                                  long connectionId,
                                  Map<Long, PricePolicyEntity> policyById,
                                  Set<Long> downgradedPolicyIds) {
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

            if (mode == ExecutionMode.FULL_AUTO
                    && downgradedPolicyIds.contains(decision.getPricePolicyId())) {
                mode = ExecutionMode.SEMI_AUTO;
            }

            PricePolicyEntity policy = decision.getPricePolicyId() != null
                    ? policyById.get(decision.getPricePolicyId()) : null;
            int approvalTimeoutHours = policy != null
                    ? resolveApprovalTimeout(policy) : 72;

            actionScheduler.scheduleAction(
                    decision.getId(),
                    decision.getMarketplaceOfferId(),
                    decision.getTargetPrice(),
                    decision.getCurrentPrice(),
                    mode,
                    connectionId,
                    workspaceId,
                    approvalTimeoutHours);
        }
    }

    private int resolveApprovalTimeout(PricePolicyEntity policy) {
        Integer timeout = policy.getApprovalTimeoutHours();
        return timeout != null && timeout > 0 ? timeout : 72;
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

    private void completeRun(PricingRunEntity run, RunCounters counters,
                             Exception partialError) {
        run.setChangeCount(counters.changeCount);
        run.setSkipCount(counters.skipCount);
        run.setHoldCount(counters.holdCount);
        run.setCompletedAt(OffsetDateTime.now());

        if (partialError != null) {
            run.setStatus(RunStatus.COMPLETED_WITH_ERRORS);
            run.setErrorDetails(serializeError(partialError));
        } else if (run.getStatus() != RunStatus.PAUSED) {
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

    private PriceDecisionEntity buildDecision(long runId, long workspaceId,
                                               EligibleOffer offer,
                                               PricePolicyEntity policy,
                                               PolicySnapshot snapshot,
                                               PricingSignalSet signals,
                                               DecisionType decisionType,
                                               BigDecimal targetPrice, BigDecimal rawPrice,
                                               StrategyResult strategyResult,
                                               List<ConstraintRecord> constraints,
                                               List<GuardEvaluationRecord> guards,
                                               String explanation,
                                               String skipReasonKey) {
        var entity = new PriceDecisionEntity();
        entity.setWorkspaceId(workspaceId);
        entity.setPricingRunId(runId);
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

        RunCounters add(RunCounters other) {
            return new RunCounters(
                    changeCount + other.changeCount,
                    skipCount + other.skipCount,
                    holdCount + other.holdCount);
        }
    }

    private record RunResult(
            int totalOffers,
            int eligibleCount,
            RunCounters counters,
            Exception partialError) {

        static RunResult empty(int totalOffers, int eligibleCount) {
            return new RunResult(totalOffers, eligibleCount, RunCounters.EMPTY, null);
        }
    }

    private record BatchResult(RunCounters counters, boolean paused) {}
}
