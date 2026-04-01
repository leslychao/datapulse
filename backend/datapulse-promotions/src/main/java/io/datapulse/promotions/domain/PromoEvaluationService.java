package io.datapulse.promotions.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.promotions.persistence.PromoActionEntity;
import io.datapulse.promotions.persistence.PromoActionRepository;
import io.datapulse.promotions.persistence.PromoDecisionEntity;
import io.datapulse.promotions.persistence.PromoDecisionRepository;
import io.datapulse.promotions.persistence.PromoEvaluationEntity;
import io.datapulse.promotions.persistence.PromoEvaluationRepository;
import io.datapulse.promotions.persistence.PromoEvaluationRunEntity;
import io.datapulse.promotions.persistence.PromoEvaluationRunRepository;
import io.datapulse.promotions.persistence.PromoPolicyEntity;
import io.datapulse.promotions.persistence.PromoProductRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates a promo evaluation run for a marketplace connection.
 * <p>
 * Pipeline:
 * 1. Load active/upcoming campaigns for connection
 * 2. Load ELIGIBLE/PARTICIPATING promo products
 * 3. Resolve effective promo_policy per offer
 * 4. Per-product: evaluate margin, stock, categories → decision → action scheduling
 * 5. Batch save evaluations, decisions, actions
 * 6. Update run counters and publish completion event
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromoEvaluationService {

    private final PromoEvaluationRunRepository runRepository;
    private final PromoEvaluationRepository evaluationRepository;
    private final PromoDecisionRepository decisionRepository;
    private final PromoActionRepository actionRepository;
    private final PromoPolicyResolver policyResolver;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void executeRun(long runId) {
        PromoEvaluationRunEntity run = runRepository.findById(runId)
                .orElseThrow(() -> new IllegalStateException("PromoEvaluationRun not found: " + runId));

        if (run.getStatus() != PromoRunStatus.PENDING) {
            log.warn("PromoEvaluationRun {} is not PENDING (status={}), skipping", runId, run.getStatus());
            return;
        }

        run.setStatus(PromoRunStatus.IN_PROGRESS);
        run.setStartedAt(OffsetDateTime.now());
        runRepository.save(run);

        try {
            RunCounters counters = processEvaluation(run);
            completeRun(run, counters);
        } catch (Exception e) {
            log.error("PromoEvaluationRun {} failed: {}", runId, e.getMessage(), e);
            failRun(run, e);
        }
    }

    private RunCounters processEvaluation(PromoEvaluationRunEntity run) {
        List<PromoProductRow> products = policyResolver.loadEligibleProducts(
                run.getConnectionId(), run.getWorkspaceId());

        run.setTotalProducts(products.size());

        if (products.isEmpty()) {
            log.info("PromoEvaluationRun {}: no eligible promo products for connection {}",
                    run.getId(), run.getConnectionId());
            return RunCounters.EMPTY;
        }

        run.setEligibleCount(products.size());

        return processProducts(run, products);
    }

    private RunCounters processProducts(PromoEvaluationRunEntity run, List<PromoProductRow> products) {
        int participateCount = 0;
        int declineCount = 0;
        int pendingReviewCount = 0;
        int deactivateCount = 0;
        int skippedStableCount = 0;
        int errorCount = 0;

        List<PromoEvaluationEntity> evaluations = new ArrayList<>();
        List<PromoDecisionEntity> decisions = new ArrayList<>();
        List<PromoActionEntity> actions = new ArrayList<>();

        for (PromoProductRow product : products) {
            try {
                PromoPolicyEntity policy = policyResolver.resolvePolicy(
                        product.marketplaceOfferId(), product.categoryId(), run.getConnectionId(),
                        run.getWorkspaceId());

                if (policy == null) {
                    continue;
                }

                EvaluationOutcome outcome = evaluateSingleProduct(run, product, policy);

                evaluations.add(outcome.evaluation());
                if (outcome.decision() != null) {
                    decisions.add(outcome.decision());
                }
                if (outcome.action() != null) {
                    actions.add(outcome.action());
                }

                if (outcome.evaluation().getSkipReason() != null
                        && "stable_state".equals(outcome.evaluation().getSkipReason())) {
                    skippedStableCount++;
                } else {
                    switch (outcome.decisionType()) {
                        case PARTICIPATE -> participateCount++;
                        case DECLINE -> declineCount++;
                        case PENDING_REVIEW -> pendingReviewCount++;
                        case DEACTIVATE -> deactivateCount++;
                    }
                }
            } catch (Exception e) {
                log.warn("Error evaluating promo product: promoProductId={}, error={}",
                        product.promoProductId(), e.getMessage());
                errorCount++;

                PromoEvaluationEntity errorEval = buildErrorEvaluation(run, product, e.getMessage());
                evaluations.add(errorEval);
            }
        }

        evaluationRepository.saveAll(evaluations);
        if (!decisions.isEmpty()) {
            decisionRepository.saveAll(decisions);
        }
        if (!actions.isEmpty()) {
            saveActionsWithConflictHandling(actions);
        }

        if (skippedStableCount > 0) {
            log.debug("PromoEvaluationRun: skipped {} stable-state products", skippedStableCount);
        }

        PromoRunStatus finalStatus = errorCount > 0
                ? PromoRunStatus.COMPLETED_WITH_ERRORS
                : PromoRunStatus.COMPLETED;

        return new RunCounters(participateCount, declineCount, pendingReviewCount, deactivateCount, finalStatus);
    }

    private EvaluationOutcome evaluateSingleProduct(PromoEvaluationRunEntity run,
                                                     PromoProductRow product,
                                                     PromoPolicyEntity policy) {
        PromoPolicySnapshot snapshot = buildSnapshot(policy);
        boolean isParticipating = "PARTICIPATING".equals(product.participationStatus());

        BigDecimal promoPrice = product.requiredPrice();
        BigDecimal regularPrice = product.currentPrice();
        BigDecimal cogs = product.cogs();

        PromoEvaluationEntity eval = new PromoEvaluationEntity();
        eval.setWorkspaceId(run.getWorkspaceId());
        eval.setPromoEvaluationRunId(run.getId());
        eval.setCanonicalPromoProductId(product.promoProductId());
        eval.setPromoPolicyId(policy.getId());
        eval.setEvaluatedAt(OffsetDateTime.now());
        eval.setCurrentParticipationStatus(product.participationStatus());
        eval.setPromoPrice(promoPrice);
        eval.setRegularPrice(regularPrice);
        eval.setCogs(cogs);
        eval.setStockAvailable(product.stockAvailable());

        if (promoPrice != null && regularPrice != null && regularPrice.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal discountPct = regularPrice.subtract(promoPrice)
                    .divide(regularPrice, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            eval.setDiscountPct(discountPct);
        }

        if (product.dateFrom() != null && product.dateTo() != null) {
            long durationDays = ChronoUnit.DAYS.between(product.dateFrom(), product.dateTo());
            eval.setExpectedPromoDurationDays((int) Math.max(durationDays, 1));
        }

        BigDecimal effectiveCostRate = product.effectiveCostRate();
        eval.setEffectiveCostRate(effectiveCostRate);

        if (promoPrice != null && cogs != null && promoPrice.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal marginAtPromo = promoPrice.subtract(cogs)
                    .divide(promoPrice, 4, RoundingMode.HALF_UP);
            if (effectiveCostRate != null) {
                marginAtPromo = marginAtPromo.subtract(effectiveCostRate);
            }
            eval.setMarginAtPromoPrice(marginAtPromo);
        }

        if (regularPrice != null && cogs != null && regularPrice.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal marginAtRegular = regularPrice.subtract(cogs)
                    .divide(regularPrice, 4, RoundingMode.HALF_UP);
            if (effectiveCostRate != null) {
                marginAtRegular = marginAtRegular.subtract(effectiveCostRate);
            }
            eval.setMarginAtRegularPrice(marginAtRegular);
        }

        if (eval.getMarginAtPromoPrice() != null && eval.getMarginAtRegularPrice() != null) {
            eval.setMarginDeltaPct(eval.getMarginAtPromoPrice().subtract(eval.getMarginAtRegularPrice())
                    .multiply(BigDecimal.valueOf(100)));
        }

        BigDecimal avgDailyVelocity = product.avgDailyVelocity();
        eval.setAvgDailyVelocity(avgDailyVelocity);

        if (product.stockAvailable() != null && avgDailyVelocity != null
                && avgDailyVelocity.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal daysCover = BigDecimal.valueOf(product.stockAvailable())
                    .divide(avgDailyVelocity, 2, RoundingMode.HALF_UP);
            eval.setStockDaysOfCover(daysCover);
            eval.setStockSufficient(daysCover.intValue() >= policy.getMinStockDaysOfCover());
        }

        PromoEvaluationResult result = classifyResult(eval, policy);
        eval.setEvaluationResult(result);

        if (isParticipating && isStableState(product.promoProductId(), result, eval.getMarginAtPromoPrice())) {
            eval.setSkipReason("stable_state");
            return new EvaluationOutcome(eval, null, null, PromoDecisionType.PARTICIPATE);
        }

        PromoDecisionType decisionType = mapDecision(result, isParticipating, policy.getParticipationMode());

        if (decisionType == null) {
            return new EvaluationOutcome(eval, null, null, result == PromoEvaluationResult.PROFITABLE
                    ? PromoDecisionType.PARTICIPATE : PromoDecisionType.DECLINE);
        }

        PromoDecisionEntity decision = buildDecision(run, product, policy, snapshot, eval, decisionType);
        PromoActionEntity action = buildAction(
                run, product, policy, decision, decisionType, isParticipating, result);

        return new EvaluationOutcome(eval, decision, action, decisionType);
    }

    private static final BigDecimal STABLE_STATE_THRESHOLD = new BigDecimal("0.01");

    /**
     * Checks if a PARTICIPATING product is in a stable state compared to its previous evaluation.
     * Stable = same evaluation_result AND margin delta < 1 pp.
     */
    private boolean isStableState(long promoProductId, PromoEvaluationResult currentResult,
                                   BigDecimal currentMargin) {
        if (currentMargin == null) {
            return false;
        }

        return evaluationRepository
                .findFirstByCanonicalPromoProductIdOrderByCreatedAtDesc(promoProductId)
                .filter(prev -> prev.getEvaluationResult() == currentResult)
                .filter(prev -> currentResult == PromoEvaluationResult.PROFITABLE)
                .filter(prev -> prev.getMarginAtPromoPrice() != null)
                .filter(prev -> currentMargin.subtract(prev.getMarginAtPromoPrice()).abs()
                        .compareTo(STABLE_STATE_THRESHOLD) < 0)
                .isPresent();
    }

    private PromoEvaluationResult classifyResult(PromoEvaluationEntity eval, PromoPolicyEntity policy) {
        if (eval.getCogs() == null || eval.getMarginAtPromoPrice() == null) {
            return PromoEvaluationResult.INSUFFICIENT_DATA;
        }

        if (eval.getStockSufficient() != null && !eval.getStockSufficient()) {
            return PromoEvaluationResult.INSUFFICIENT_STOCK;
        }

        BigDecimal marginPct = eval.getMarginAtPromoPrice().multiply(BigDecimal.valueOf(100));
        BigDecimal minMargin = policy.getMinMarginPct();

        if (marginPct.compareTo(minMargin) >= 0) {
            return PromoEvaluationResult.PROFITABLE;
        }

        if (marginPct.compareTo(BigDecimal.ZERO) > 0) {
            return PromoEvaluationResult.MARGINAL;
        }

        return PromoEvaluationResult.UNPROFITABLE;
    }

    /**
     * Maps evaluation result + participation status + mode to decision type.
     * Returns null when no action is needed (e.g. PROFITABLE PARTICIPATING → continue).
     */
    private PromoDecisionType mapDecision(PromoEvaluationResult result, boolean isParticipating,
                                           ParticipationMode mode) {
        if (isParticipating) {
            return mapDecisionForParticipating(result, mode);
        }
        return mapDecisionForEligible(result, mode);
    }

    private PromoDecisionType mapDecisionForEligible(PromoEvaluationResult result, ParticipationMode mode) {
        return switch (result) {
            case PROFITABLE -> PromoDecisionType.PARTICIPATE;
            case MARGINAL -> PromoDecisionType.PENDING_REVIEW;
            case UNPROFITABLE, INSUFFICIENT_STOCK -> PromoDecisionType.DECLINE;
            case INSUFFICIENT_DATA -> PromoDecisionType.PENDING_REVIEW;
        };
    }

    private PromoDecisionType mapDecisionForParticipating(PromoEvaluationResult result, ParticipationMode mode) {
        return switch (result) {
            case PROFITABLE -> null;
            case MARGINAL -> mode == ParticipationMode.RECOMMENDATION ? PromoDecisionType.PENDING_REVIEW : null;
            case UNPROFITABLE -> PromoDecisionType.DEACTIVATE;
            case INSUFFICIENT_STOCK -> PromoDecisionType.DEACTIVATE;
            case INSUFFICIENT_DATA -> null;
        };
    }

    private PromoDecisionEntity buildDecision(PromoEvaluationRunEntity run, PromoProductRow product,
                                               PromoPolicyEntity policy, PromoPolicySnapshot snapshot,
                                               PromoEvaluationEntity eval, PromoDecisionType decisionType) {
        var decision = new PromoDecisionEntity();
        decision.setWorkspaceId(run.getWorkspaceId());
        decision.setCanonicalPromoProductId(product.promoProductId());
        decision.setPromoEvaluationId(eval.getId());
        decision.setPolicyVersion(policy.getVersion());
        decision.setPolicySnapshot(serializeJson(snapshot));
        decision.setDecisionType(decisionType);
        decision.setParticipationMode(policy.getParticipationMode());
        decision.setExecutionMode(
                policy.getParticipationMode() == ParticipationMode.SIMULATED ? "SIMULATED" : "LIVE");
        decision.setTargetPromoPrice(product.requiredPrice());

        String explanation = buildExplanation(eval, decisionType, policy);
        decision.setExplanationSummary(explanation);

        return decision;
    }

    private PromoActionEntity buildAction(PromoEvaluationRunEntity run, PromoProductRow product,
                                           PromoPolicyEntity policy, PromoDecisionEntity decision,
                                           PromoDecisionType decisionType, boolean isParticipating,
                                           PromoEvaluationResult evaluationResult) {
        if (decisionType == PromoDecisionType.DECLINE) {
            return null;
        }
        if (policy.getParticipationMode() == ParticipationMode.RECOMMENDATION) {
            return null;
        }

        PromoActionType actionType = decisionType == PromoDecisionType.DEACTIVATE
                ? PromoActionType.DEACTIVATE
                : PromoActionType.ACTIVATE;

        PromoActionStatus initialStatus = resolveActionStatus(
                policy.getParticipationMode(), decisionType, evaluationResult);

        var action = new PromoActionEntity();
        action.setWorkspaceId(run.getWorkspaceId());
        action.setPromoDecisionId(decision.getId());
        action.setCanonicalPromoCampaignId(product.campaignId());
        action.setMarketplaceOfferId(product.marketplaceOfferId());
        action.setActionType(actionType);
        action.setTargetPromoPrice(
                actionType == PromoActionType.ACTIVATE ? product.requiredPrice() : null);
        action.setStatus(initialStatus);
        action.setAttemptCount(0);
        action.setExecutionMode(
                policy.getParticipationMode() == ParticipationMode.SIMULATED
                        ? PromoExecutionMode.SIMULATED : PromoExecutionMode.LIVE);
        action.setFreezeAtSnapshot(product.freezeAt());

        return action;
    }

    private PromoActionStatus resolveActionStatus(ParticipationMode mode,
                                                   PromoDecisionType decisionType,
                                                   PromoEvaluationResult evaluationResult) {
        if (decisionType == PromoDecisionType.PENDING_REVIEW) {
            return PromoActionStatus.PENDING_APPROVAL;
        }
        if (mode == ParticipationMode.FULL_AUTO
                && evaluationResult == PromoEvaluationResult.INSUFFICIENT_STOCK
                && decisionType == PromoDecisionType.DEACTIVATE) {
            return PromoActionStatus.PENDING_APPROVAL;
        }
        return switch (mode) {
            case SEMI_AUTO -> PromoActionStatus.PENDING_APPROVAL;
            case FULL_AUTO, SIMULATED -> PromoActionStatus.APPROVED;
            case RECOMMENDATION -> throw new IllegalStateException("RECOMMENDATION should not create actions");
        };
    }

    private void saveActionsWithConflictHandling(List<PromoActionEntity> actions) {
        for (PromoActionEntity action : actions) {
            try {
                actionRepository.save(action);
            } catch (Exception e) {
                log.warn("Failed to save promo action (likely duplicate): campaignId={}, offerId={}, error={}",
                        action.getCanonicalPromoCampaignId(), action.getMarketplaceOfferId(), e.getMessage());
            }
        }
    }

    private PromoEvaluationEntity buildErrorEvaluation(PromoEvaluationRunEntity run,
                                                        PromoProductRow product, String errorMessage) {
        var eval = new PromoEvaluationEntity();
        eval.setWorkspaceId(run.getWorkspaceId());
        eval.setPromoEvaluationRunId(run.getId());
        eval.setCanonicalPromoProductId(product.promoProductId());
        eval.setEvaluatedAt(OffsetDateTime.now());
        eval.setCurrentParticipationStatus(product.participationStatus());
        eval.setEvaluationResult(PromoEvaluationResult.INSUFFICIENT_DATA);
        eval.setSkipReason(errorMessage);
        return eval;
    }

    private void completeRun(PromoEvaluationRunEntity run, RunCounters counters) {
        run.setParticipateCount(counters.participateCount());
        run.setDeclineCount(counters.declineCount());
        run.setPendingReviewCount(counters.pendingReviewCount());
        run.setDeactivateCount(counters.deactivateCount());
        run.setCompletedAt(OffsetDateTime.now());
        run.setStatus(counters.status());
        runRepository.save(run);

        eventPublisher.publishEvent(new PromoEvaluationCompletedEvent(
                run.getId(), run.getWorkspaceId(), run.getConnectionId(),
                counters.participateCount(), counters.declineCount(),
                counters.pendingReviewCount(), counters.deactivateCount(),
                counters.status()));
    }

    private void failRun(PromoEvaluationRunEntity run, Exception error) {
        run.setStatus(PromoRunStatus.FAILED);
        run.setCompletedAt(OffsetDateTime.now());
        run.setErrorDetails(serializeJson(Map.of(
                "type", error.getClass().getSimpleName(),
                "message", error.getMessage() != null ? error.getMessage() : "Unknown error")));
        runRepository.save(run);

        eventPublisher.publishEvent(new PromoEvaluationCompletedEvent(
                run.getId(), run.getWorkspaceId(), run.getConnectionId(),
                0, 0, 0, 0, PromoRunStatus.FAILED));
    }

    private PromoPolicySnapshot buildSnapshot(PromoPolicyEntity policy) {
        return new PromoPolicySnapshot(
                policy.getId(), policy.getVersion(), policy.getName(),
                policy.getParticipationMode(), policy.getMinMarginPct(),
                policy.getMinStockDaysOfCover(), policy.getMaxPromoDiscountPct(),
                policy.getAutoParticipateCategories(), policy.getAutoDeclineCategories(),
                policy.getEvaluationConfig());
    }

    private String buildExplanation(PromoEvaluationEntity eval, PromoDecisionType decisionType,
                                     PromoPolicyEntity policy) {
        var sb = new StringBuilder();
        sb.append("result=").append(eval.getEvaluationResult());
        sb.append(", decision=").append(decisionType);
        sb.append(", mode=").append(policy.getParticipationMode());

        if (eval.getMarginAtPromoPrice() != null) {
            sb.append(", margin_at_promo=").append(eval.getMarginAtPromoPrice()
                    .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)).append("%");
        }
        if (eval.getStockDaysOfCover() != null) {
            sb.append(", stock_cover=").append(eval.getStockDaysOfCover()).append("d");
        }
        sb.append(", min_margin=").append(policy.getMinMarginPct()).append("%");

        return sb.toString();
    }

    private String serializeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.error("JSON serialization failed", e);
            return "{}";
        }
    }

    private record RunCounters(int participateCount, int declineCount,
                                int pendingReviewCount, int deactivateCount,
                                PromoRunStatus status) {
        static final RunCounters EMPTY = new RunCounters(0, 0, 0, 0, PromoRunStatus.COMPLETED);
    }

    private record EvaluationOutcome(PromoEvaluationEntity evaluation,
                                      PromoDecisionEntity decision,
                                      PromoActionEntity action,
                                      PromoDecisionType decisionType) {
    }
}
