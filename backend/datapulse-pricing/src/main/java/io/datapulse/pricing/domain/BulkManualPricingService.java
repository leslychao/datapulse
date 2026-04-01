package io.datapulse.pricing.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.common.error.MessageCodes;
import io.datapulse.common.exception.BadRequestException;
import io.datapulse.platform.audit.AutomationBlockerChecker;
import io.datapulse.pricing.api.BulkManualApplyResponse;
import io.datapulse.pricing.api.BulkManualPreviewRequest;
import io.datapulse.pricing.api.BulkManualPreviewRequest.PriceChange;
import io.datapulse.pricing.api.BulkManualPreviewResponse;
import io.datapulse.pricing.api.BulkManualPreviewResponse.ConstraintApplied;
import io.datapulse.pricing.api.BulkManualPreviewResponse.OfferPreview;
import io.datapulse.pricing.api.BulkManualPreviewResponse.Summary;
import io.datapulse.pricing.domain.PricingConstraintResolver.ConstraintResolution;
import io.datapulse.pricing.domain.guard.PricingGuardChain;
import io.datapulse.pricing.domain.guard.PricingGuardChain.GuardChainResult;
import io.datapulse.pricing.persistence.PriceDecisionEntity;
import io.datapulse.pricing.persistence.PriceDecisionRepository;
import io.datapulse.pricing.persistence.PricingDataReadRepository;
import io.datapulse.pricing.persistence.PricingDataReadRepository.EnrichedOfferRow;
import io.datapulse.pricing.persistence.PricingRunEntity;
import io.datapulse.pricing.persistence.PricingRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BulkManualPricingService {

    private final AutomationBlockerChecker automationBlockerChecker;
    private final PricingDataReadRepository dataReadRepository;
    private final PricingRunRepository runRepository;
    private final PriceDecisionRepository decisionRepository;
    private final PricingConstraintResolver constraintResolver;
    private final PricingGuardChain guardChain;
    private final PricingActionScheduler actionScheduler;
    private final ExplanationBuilder explanationBuilder;
    private final ObjectMapper objectMapper;

    private static final GuardConfig BULK_GUARD_CONFIG = new GuardConfig(
            true, false, null, false, null, null, true, false, null
    );

    @Transactional(readOnly = true)
    public BulkManualPreviewResponse preview(BulkManualPreviewRequest request,
                                             long workspaceId) {
        Map<Long, EnrichedOfferRow> offers = loadOffers(request, workspaceId);
        List<OfferPreview> previews = new ArrayList<>();
        var stats = new PreviewStats();

        for (PriceChange change : request.changes()) {
            OfferPreview preview = evaluateOffer(change, offers.get(change.marketplaceOfferId()), stats);
            previews.add(preview);
        }

        return new BulkManualPreviewResponse(stats.buildSummary(request.changes().size()), previews);
    }

    @Transactional
    public BulkManualApplyResponse apply(BulkManualPreviewRequest request,
                                         long workspaceId, long userId) {
        String hash = computeRequestHash(request);
        ensureNotDuplicate(hash);

        Map<Long, EnrichedOfferRow> offers = loadOffers(request, workspaceId);
        Long connectionId = resolveConnectionId(offers);

        if (automationBlockerChecker.isBlocked(workspaceId, connectionId)) {
            throw BadRequestException.of(MessageCodes.PRICING_AUTOMATION_BLOCKED);
        }

        PricingRunEntity run = createRun(workspaceId, connectionId, hash, request.changes().size());
        return processApply(request, offers, run, workspaceId);
    }

    private OfferPreview evaluateOffer(PriceChange change, EnrichedOfferRow offer,
                                       PreviewStats stats) {
        if (offer == null) {
            stats.skip();
            return buildSkipPreview(change, null, MessageCodes.ENTITY_NOT_FOUND, null);
        }

        BigDecimal currentPrice = offer.currentPrice();
        PolicySnapshot snapshot = buildBulkSnapshot(offer);
        PricingSignalSet signals = buildBulkSignals(offer, currentPrice);

        ConstraintResolution constrained = constraintResolver.resolve(
                change.targetPrice(), signals, snapshot);
        BigDecimal effectivePrice = constrained.clampedPrice();

        GuardChainResult guardResult = guardChain.evaluate(signals, effectivePrice, BULK_GUARD_CONFIG);
        if (!guardResult.allPassed()) {
            stats.block();
            return buildSkipPreview(change, offer,
                    guardResult.blockingGuard().reason(), guardResult.blockingGuard().guardName());
        }

        if (currentPrice != null && effectivePrice.compareTo(currentPrice) == 0) {
            stats.skip();
            return buildSkipPreview(change, offer, MessageCodes.PRICING_NO_CHANGE, null);
        }

        stats.change(currentPrice, effectivePrice);
        BigDecimal marginValue = computeMargin(effectivePrice, offer.cogs());
        stats.trackMargin(marginValue);

        List<ConstraintApplied> constraints = constrained.applied().stream()
                .map(c -> new ConstraintApplied(c.name(), c.fromPrice(), c.toPrice()))
                .toList();

        return new OfferPreview(
                change.marketplaceOfferId(), offer.skuCode(), offer.productName(),
                currentPrice, change.targetPrice(), effectivePrice, "CHANGE",
                constraints, marginValue, null, null);
    }

    private BulkManualApplyResponse processApply(BulkManualPreviewRequest request,
                                                  Map<Long, EnrichedOfferRow> offers,
                                                  PricingRunEntity run, long workspaceId) {
        int processed = 0;
        int skipped = 0;
        int errored = 0;
        List<String> errors = new ArrayList<>();
        List<PriceDecisionEntity> decisions = new ArrayList<>();

        for (PriceChange change : request.changes()) {
            EnrichedOfferRow offer = offers.get(change.marketplaceOfferId());
            if (offer == null) {
                skipped++;
                continue;
            }
            try {
                PriceDecisionEntity decision = buildAndEvaluateDecision(run, offer, change.targetPrice());
                if (decision != null) {
                    decisions.add(decision);
                    processed++;
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                errored++;
                errors.add("offerId=%d: %s".formatted(change.marketplaceOfferId(), e.getMessage()));
                log.warn("Bulk manual: failed to process offer {}: {}",
                        change.marketplaceOfferId(), e.getMessage());
            }
        }

        if (!decisions.isEmpty()) {
            decisionRepository.saveAll(decisions);
            for (PriceDecisionEntity d : decisions) {
                if (d.getDecisionType() == DecisionType.CHANGE) {
                    actionScheduler.scheduleAction(
                            d.getId(), d.getMarketplaceOfferId(), d.getTargetPrice(),
                            ExecutionMode.FULL_AUTO, workspaceId);
                }
            }
        }

        completeRun(run, request.changes().size(), processed, skipped, errors);
        return new BulkManualApplyResponse(run.getId(), processed, skipped, errored, errors);
    }

    private PriceDecisionEntity buildAndEvaluateDecision(PricingRunEntity run,
                                                          EnrichedOfferRow offer,
                                                          BigDecimal targetPrice) {
        BigDecimal currentPrice = offer.currentPrice();
        PolicySnapshot snapshot = buildBulkSnapshot(offer);
        PricingSignalSet signals = buildBulkSignals(offer, currentPrice);

        ConstraintResolution constrained = constraintResolver.resolve(
                targetPrice, signals, snapshot);
        BigDecimal effectivePrice = constrained.clampedPrice();

        GuardChainResult guardResult = guardChain.evaluate(signals, effectivePrice, BULK_GUARD_CONFIG);
        if (!guardResult.allPassed()) {
            return null;
        }

        if (currentPrice != null && effectivePrice.compareTo(currentPrice) == 0) {
            return null;
        }

        return buildManualDecision(run, offer, targetPrice, effectivePrice,
                currentPrice, constrained, guardResult);
    }

    private PriceDecisionEntity buildManualDecision(PricingRunEntity run, EnrichedOfferRow offer,
                                                     BigDecimal requestedPrice,
                                                     BigDecimal effectivePrice,
                                                     BigDecimal currentPrice,
                                                     ConstraintResolution constrained,
                                                     GuardChainResult guardResult) {
        var entity = new PriceDecisionEntity();
        entity.setWorkspaceId(run.getWorkspaceId());
        entity.setPricingRunId(run.getId());
        entity.setMarketplaceOfferId(offer.id());
        entity.setPricePolicyId(null);
        entity.setPolicyVersion(0);
        entity.setPolicySnapshot(null);
        entity.setDecisionType(DecisionType.CHANGE);
        entity.setCurrentPrice(currentPrice);
        entity.setTargetPrice(effectivePrice);
        entity.setStrategyType(PolicyType.MANUAL_OVERRIDE);
        entity.setStrategyRawPrice(requestedPrice);
        entity.setSignalSnapshot(serializeJson(Map.of(
                "current_price", currentPrice != null ? currentPrice : BigDecimal.ZERO,
                "cost_price", offer.cogs() != null ? offer.cogs() : BigDecimal.ZERO,
                "source", "MANUAL_BULK")));
        entity.setConstraintsApplied(serializeJson(constrained.applied()));
        entity.setGuardsEvaluated(serializeJson(guardResult.evaluations()));
        entity.setExecutionMode("LIVE");

        if (currentPrice != null) {
            BigDecimal changeAmount = effectivePrice.subtract(currentPrice);
            entity.setPriceChangeAmount(changeAmount);
            if (currentPrice.compareTo(BigDecimal.ZERO) != 0) {
                entity.setPriceChangePct(changeAmount
                        .divide(currentPrice, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)));
            }
        }

        entity.setExplanationSummary(buildBulkExplanation(
                currentPrice, effectivePrice, requestedPrice, constrained, run.getId()));
        return entity;
    }

    private String buildBulkExplanation(BigDecimal currentPrice, BigDecimal effectivePrice,
                                        BigDecimal requestedPrice,
                                        ConstraintResolution constrained, long runId) {
        var sb = new StringBuilder();
        sb.append("[Решение] CHANGE: %s → %s\n".formatted(
                currentPrice != null ? currentPrice.toPlainString() : "N/A",
                effectivePrice.toPlainString()));
        sb.append("[Источник] Ручное массовое изменение (MANUAL_BULK run #%d)\n".formatted(runId));
        sb.append("[Запрос] Целевая цена: %s ₽\n".formatted(requestedPrice.toPlainString()));

        if (!constrained.applied().isEmpty()) {
            sb.append("[Ограничения] ");
            for (int i = 0; i < constrained.applied().size(); i++) {
                ConstraintRecord c = constrained.applied().get(i);
                sb.append("%s: %s → %s".formatted(
                        c.name(), c.fromPrice().toPlainString(), c.toPrice().toPlainString()));
                if (i < constrained.applied().size() - 1) {
                    sb.append("; ");
                }
            }
            sb.append("\n");
        }
        sb.append("[Guards] Все пройдены\n");
        sb.append("[Режим] APPROVED → action APPROVED");
        return sb.toString();
    }

    private PolicySnapshot buildBulkSnapshot(EnrichedOfferRow offer) {
        return new PolicySnapshot(0, 0, "MANUAL_BULK", PolicyType.MANUAL_OVERRIDE,
                "{}", null, null, null, null, null, ExecutionMode.FULL_AUTO);
    }

    private PricingSignalSet buildBulkSignals(EnrichedOfferRow offer, BigDecimal currentPrice) {
        return new PricingSignalSet(
                currentPrice, offer.cogs(), offer.status(), null,
                false, false,
                null, null, null, null,
                null, null, null);
    }

    private void ensureNotDuplicate(String hash) {
        boolean duplicate = runRepository.existsByRequestHashAndTriggerTypeAndStatusNotIn(
                hash, RunTriggerType.MANUAL_BULK,
                List.of(RunStatus.COMPLETED, RunStatus.COMPLETED_WITH_ERRORS, RunStatus.FAILED));
        if (duplicate) {
            throw BadRequestException.of(MessageCodes.PRICING_BULK_DUPLICATE);
        }
    }

    private PricingRunEntity createRun(long workspaceId, long connectionId,
                                       String hash, int requestedCount) {
        var run = new PricingRunEntity();
        run.setWorkspaceId(workspaceId);
        run.setConnectionId(connectionId);
        run.setTriggerType(RunTriggerType.MANUAL_BULK);
        run.setRequestHash(hash);
        run.setRequestedOffersCount(requestedCount);
        run.setStatus(RunStatus.IN_PROGRESS);
        run.setStartedAt(OffsetDateTime.now());
        return runRepository.save(run);
    }

    private void completeRun(PricingRunEntity run, int total, int created,
                             int skipped, List<String> errors) {
        run.setTotalOffers(total);
        run.setEligibleCount(created + skipped);
        run.setChangeCount(created);
        run.setSkipCount(skipped);
        run.setHoldCount(0);
        run.setCompletedAt(OffsetDateTime.now());
        run.setStatus(errors.isEmpty() ? RunStatus.COMPLETED : RunStatus.COMPLETED_WITH_ERRORS);
        runRepository.save(run);

        log.info("Bulk manual apply completed: runId={}, created={}, skipped={}, errors={}",
                run.getId(), created, skipped, errors.size());
    }

    private Map<Long, EnrichedOfferRow> loadOffers(BulkManualPreviewRequest request,
                                                    long workspaceId) {
        List<Long> offerIds = request.changes().stream()
                .map(PriceChange::marketplaceOfferId)
                .toList();
        return dataReadRepository.findOffersByIds(offerIds, workspaceId).stream()
                .collect(Collectors.toMap(EnrichedOfferRow::id, Function.identity()));
    }

    private Long resolveConnectionId(Map<Long, EnrichedOfferRow> offers) {
        return offers.values().stream()
                .map(EnrichedOfferRow::connectionId)
                .findFirst()
                .orElse(0L);
    }

    private BigDecimal computeMargin(BigDecimal price, BigDecimal cogs) {
        if (price == null || cogs == null || price.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return price.subtract(cogs)
                .divide(price, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    private OfferPreview buildSkipPreview(PriceChange change, EnrichedOfferRow offer,
                                          String skipReason, String guard) {
        return new OfferPreview(
                change.marketplaceOfferId(),
                offer != null ? offer.skuCode() : null,
                offer != null ? offer.productName() : null,
                offer != null ? offer.currentPrice() : null,
                change.targetPrice(), null, "SKIP",
                List.of(), null, skipReason, guard);
    }

    private String computeRequestHash(BulkManualPreviewRequest request) {
        List<String> sorted = request.changes().stream()
                .map(c -> c.marketplaceOfferId() + ":" + c.targetPrice().toPlainString())
                .sorted()
                .toList();
        String joined = String.join(",", sorted);

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(joined.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String serializeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.error("JSON serialization failed", e);
            return "{}";
        }
    }

    private static class PreviewStats {
        int willChange;
        int willSkip;
        int willBlock;
        BigDecimal totalChangePct = BigDecimal.ZERO;
        BigDecimal maxChangePct = BigDecimal.ZERO;
        BigDecimal minMargin;

        void change(BigDecimal currentPrice, BigDecimal effectivePrice) {
            willChange++;
            if (currentPrice != null && currentPrice.compareTo(BigDecimal.ZERO) != 0) {
                BigDecimal pct = effectivePrice.subtract(currentPrice)
                        .divide(currentPrice, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).abs();
                totalChangePct = totalChangePct.add(pct);
                if (pct.compareTo(maxChangePct) > 0) {
                    maxChangePct = pct;
                }
            }
        }

        void skip() { willSkip++; }
        void block() { willBlock++; }

        void trackMargin(BigDecimal margin) {
            if (margin != null && (minMargin == null || margin.compareTo(minMargin) < 0)) {
                minMargin = margin;
            }
        }

        Summary buildSummary(int totalRequested) {
            BigDecimal avg = willChange > 0
                    ? totalChangePct.divide(BigDecimal.valueOf(willChange), 1, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            return new Summary(totalRequested, willChange, willSkip, willBlock,
                    avg, minMargin, maxChangePct);
        }
    }
}
