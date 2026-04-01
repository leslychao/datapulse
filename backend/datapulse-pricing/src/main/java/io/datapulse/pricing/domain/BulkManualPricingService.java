package io.datapulse.pricing.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.common.error.MessageCodes;
import io.datapulse.common.exception.BadRequestException;
import io.datapulse.pricing.api.BulkManualApplyResponse;
import io.datapulse.pricing.api.BulkManualPreviewRequest;
import io.datapulse.pricing.api.BulkManualPreviewRequest.PriceChange;
import io.datapulse.pricing.api.BulkManualPreviewResponse;
import io.datapulse.pricing.api.BulkManualPreviewResponse.ConstraintApplied;
import io.datapulse.pricing.api.BulkManualPreviewResponse.OfferPreview;
import io.datapulse.pricing.api.BulkManualPreviewResponse.Summary;
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

    private final PricingDataReadRepository dataReadRepository;
    private final PricingRunRepository runRepository;
    private final PriceDecisionRepository decisionRepository;
    private final PricingActionScheduler actionScheduler;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public BulkManualPreviewResponse preview(BulkManualPreviewRequest request, long workspaceId) {
        List<Long> offerIds = request.changes().stream()
                .map(PriceChange::marketplaceOfferId)
                .toList();

        Map<Long, EnrichedOfferRow> offers = loadOffers(offerIds, workspaceId);

        List<OfferPreview> previews = new ArrayList<>();
        int willChange = 0;
        int willSkip = 0;
        int willBlock = 0;
        BigDecimal totalChangePct = BigDecimal.ZERO;
        BigDecimal minMargin = null;
        BigDecimal maxChangePct = BigDecimal.ZERO;

        for (PriceChange change : request.changes()) {
            EnrichedOfferRow offer = offers.get(change.marketplaceOfferId());
            if (offer == null) {
                previews.add(buildSkipPreview(change, null, MessageCodes.ENTITY_NOT_FOUND, null));
                willSkip++;
                continue;
            }

            BigDecimal currentPrice = offer.currentPrice();
            BigDecimal effectivePrice = change.targetPrice();
            List<ConstraintApplied> constraints = new ArrayList<>();

            String result = "CHANGE";
            String skipReason = null;
            String guard = null;

            if (currentPrice != null && effectivePrice.compareTo(currentPrice) == 0) {
                result = "SKIP";
                skipReason = MessageCodes.PRICING_NO_CHANGE;
                willSkip++;
            } else {
                willChange++;

                if (currentPrice != null && currentPrice.compareTo(BigDecimal.ZERO) != 0) {
                    BigDecimal changePct = effectivePrice.subtract(currentPrice)
                            .divide(currentPrice, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100));
                    totalChangePct = totalChangePct.add(changePct.abs());
                    if (changePct.abs().compareTo(maxChangePct) > 0) {
                        maxChangePct = changePct.abs();
                    }
                }
            }

            BigDecimal marginValue = computeMargin(effectivePrice, offer.cogs());
            if (marginValue != null && (minMargin == null || marginValue.compareTo(minMargin) < 0)) {
                minMargin = marginValue;
            }

            previews.add(new OfferPreview(
                    change.marketplaceOfferId(),
                    offer.skuCode(),
                    offer.productName(),
                    currentPrice,
                    change.targetPrice(),
                    effectivePrice,
                    result,
                    constraints,
                    marginValue,
                    skipReason,
                    guard));
        }

        BigDecimal avgChangePct = willChange > 0
                ? totalChangePct.divide(BigDecimal.valueOf(willChange), 1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return new BulkManualPreviewResponse(
                new Summary(request.changes().size(), willChange, willSkip, willBlock,
                        avgChangePct, minMargin, maxChangePct),
                previews);
    }

    @Transactional
    public BulkManualApplyResponse apply(BulkManualPreviewRequest request,
                                         long workspaceId, long userId) {
        String hash = computeRequestHash(request);
        boolean duplicate = runRepository.findAll().stream()
                .anyMatch(r -> hash.equals(r.getRequestHash())
                        && r.getTriggerType() == RunTriggerType.MANUAL_BULK
                        && !r.getStatus().isTerminal());
        if (duplicate) {
            throw BadRequestException.of(MessageCodes.PRICING_BULK_DUPLICATE);
        }

        List<Long> offerIds = request.changes().stream()
                .map(PriceChange::marketplaceOfferId)
                .toList();

        Map<Long, EnrichedOfferRow> offers = loadOffers(offerIds, workspaceId);

        Long connectionId = offers.values().stream()
                .map(EnrichedOfferRow::connectionId)
                .findFirst()
                .orElse(0L);

        var run = new PricingRunEntity();
        run.setWorkspaceId(workspaceId);
        run.setConnectionId(connectionId);
        run.setTriggerType(RunTriggerType.MANUAL_BULK);
        run.setRequestHash(hash);
        run.setRequestedOffersCount(request.changes().size());
        run.setStatus(RunStatus.IN_PROGRESS);
        run.setStartedAt(OffsetDateTime.now());
        PricingRunEntity savedRun = runRepository.save(run);

        int created = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        List<PriceDecisionEntity> decisions = new ArrayList<>();

        for (PriceChange change : request.changes()) {
            EnrichedOfferRow offer = offers.get(change.marketplaceOfferId());
            if (offer == null) {
                skipped++;
                continue;
            }

            try {
                PriceDecisionEntity decision = buildManualDecision(
                        savedRun, offer, change.targetPrice());
                decisions.add(decision);
                created++;
            } catch (Exception e) {
                skipped++;
                errors.add("offerId=%d: %s".formatted(change.marketplaceOfferId(), e.getMessage()));
                log.warn("Bulk manual: failed to process offer {}: {}", change.marketplaceOfferId(), e.getMessage());
            }
        }

        if (!decisions.isEmpty()) {
            decisionRepository.saveAll(decisions);
            scheduleActions(decisions, workspaceId);
        }

        savedRun.setTotalOffers(request.changes().size());
        savedRun.setEligibleCount(created + skipped);
        savedRun.setChangeCount(created);
        savedRun.setSkipCount(skipped);
        savedRun.setHoldCount(0);
        savedRun.setCompletedAt(OffsetDateTime.now());
        savedRun.setStatus(errors.isEmpty() ? RunStatus.COMPLETED : RunStatus.COMPLETED_WITH_ERRORS);
        runRepository.save(savedRun);

        log.info("Bulk manual apply completed: runId={}, created={}, skipped={}, errors={}",
                savedRun.getId(), created, skipped, errors.size());

        return new BulkManualApplyResponse(savedRun.getId(), created, skipped, errors);
    }

    private PriceDecisionEntity buildManualDecision(PricingRunEntity run, EnrichedOfferRow offer,
                                                     BigDecimal targetPrice) {
        var entity = new PriceDecisionEntity();
        entity.setWorkspaceId(run.getWorkspaceId());
        entity.setPricingRunId(run.getId());
        entity.setMarketplaceOfferId(offer.id());
        entity.setPricePolicyId(0L);
        entity.setPolicyVersion(0);
        entity.setPolicySnapshot("null");
        entity.setDecisionType(DecisionType.CHANGE);
        entity.setCurrentPrice(offer.currentPrice());
        entity.setTargetPrice(targetPrice);
        entity.setStrategyType(PolicyType.TARGET_MARGIN);
        entity.setStrategyRawPrice(targetPrice);
        entity.setSignalSnapshot(serializeJson(Map.of(
                "current_price", offer.currentPrice() != null ? offer.currentPrice() : BigDecimal.ZERO,
                "source", "MANUAL_BULK")));
        entity.setConstraintsApplied("[]");
        entity.setGuardsEvaluated("[]");
        entity.setExplanationSummary("[Решение] CHANGE: %s → %s\n[Источник] Ручное массовое изменение (MANUAL_BULK run #%d)"
                .formatted(
                        offer.currentPrice() != null ? offer.currentPrice().toPlainString() : "N/A",
                        targetPrice.toPlainString(),
                        run.getId()));
        entity.setExecutionMode("LIVE");

        if (offer.currentPrice() != null) {
            BigDecimal changeAmount = targetPrice.subtract(offer.currentPrice());
            entity.setPriceChangeAmount(changeAmount);
            if (offer.currentPrice().compareTo(BigDecimal.ZERO) != 0) {
                entity.setPriceChangePct(changeAmount
                        .divide(offer.currentPrice(), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)));
            }
        }

        return entity;
    }

    private void scheduleActions(List<PriceDecisionEntity> decisions, long workspaceId) {
        for (PriceDecisionEntity decision : decisions) {
            if (decision.getDecisionType() == DecisionType.CHANGE) {
                actionScheduler.scheduleAction(
                        decision.getId(),
                        decision.getMarketplaceOfferId(),
                        decision.getTargetPrice(),
                        ExecutionMode.FULL_AUTO,
                        workspaceId);
            }
        }
    }

    private Map<Long, EnrichedOfferRow> loadOffers(List<Long> offerIds, long workspaceId) {
        return dataReadRepository.findOffersByIds(offerIds, workspaceId).stream()
                .collect(Collectors.toMap(EnrichedOfferRow::id, Function.identity()));
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
                change.targetPrice(),
                null,
                "SKIP",
                List.of(),
                null,
                skipReason,
                guard);
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
}
