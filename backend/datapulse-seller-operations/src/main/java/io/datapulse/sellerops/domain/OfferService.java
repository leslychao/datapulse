package io.datapulse.sellerops.domain;

import io.datapulse.common.exception.NotFoundException;
import io.datapulse.platform.security.WorkspaceContext;
import io.datapulse.pricing.api.CreateManualLockRequest;
import io.datapulse.pricing.domain.ManualPriceLockService;
import io.datapulse.sellerops.api.ActionHistoryResponse;
import io.datapulse.sellerops.api.LockOfferRequest;
import io.datapulse.sellerops.api.OfferDetailResponse;
import io.datapulse.sellerops.api.OfferDetailResponse.ActionInfo;
import io.datapulse.sellerops.api.OfferDetailResponse.DecisionInfo;
import io.datapulse.sellerops.api.OfferDetailResponse.LockInfo;
import io.datapulse.sellerops.api.OfferDetailResponse.PolicyInfo;
import io.datapulse.sellerops.api.OfferDetailResponse.PromoInfo;
import io.datapulse.sellerops.config.GridProperties;
import io.datapulse.sellerops.persistence.ActionHistoryJdbcRepository;
import io.datapulse.sellerops.persistence.ActionHistoryRow;
import io.datapulse.sellerops.persistence.ClickHouseEnrichment;
import io.datapulse.sellerops.persistence.GridClickHouseReadRepository;
import io.datapulse.sellerops.persistence.OfferDetailJdbcRepository;
import io.datapulse.sellerops.persistence.OfferDetailRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OfferService {

  private final OfferDetailJdbcRepository detailRepository;
  private final GridClickHouseReadRepository chRepository;
  private final ActionHistoryJdbcRepository actionHistoryRepository;
  private final ManualPriceLockService lockService;
  private final WorkspaceContext workspaceContext;
  private final GridProperties gridProperties;

  @Transactional(readOnly = true)
  public OfferDetailResponse getOfferDetail(long workspaceId, long offerId) {
    OfferDetailRow row = detailRepository.findById(workspaceId, offerId)
        .orElseThrow(() -> NotFoundException.entity("marketplace_offer", offerId));

    Map<Long, ClickHouseEnrichment> enrichment =
        fetchEnrichmentSafely(List.of(offerId));
    ClickHouseEnrichment ch = enrichment.get(offerId);

    return toResponse(row, ch);
  }

  @Transactional
  public void lockOffer(long workspaceId, long offerId, LockOfferRequest request) {
    OffsetDateTime expiresAt = request.durationHours() != null
        ? OffsetDateTime.now().plusHours(request.durationHours())
        : null;

    var lockRequest = new CreateManualLockRequest(
        offerId,
        request.lockedPrice(),
        request.reason(),
        expiresAt);

    lockService.createLock(lockRequest, workspaceId, workspaceContext.getUserId());
  }

  @Transactional
  public void unlockOffer(long workspaceId, long offerId) {
    lockService.unlockByOfferId(offerId, workspaceId, workspaceContext.getUserId());
  }

  @Transactional(readOnly = true)
  public Page<ActionHistoryResponse> getActionHistory(long workspaceId, long offerId,
                                                      Pageable pageable) {
    return actionHistoryRepository.findByOfferId(workspaceId, offerId, pageable)
        .map(this::toActionHistoryResponse);
  }

  private OfferDetailResponse toResponse(OfferDetailRow row, ClickHouseEnrichment ch) {
    PolicyInfo policyInfo = row.getPolicyId() != null
        ? new PolicyInfo(
            row.getPolicyId(),
            row.getPolicyName(),
            row.getStrategyType(),
            row.getPolicyExecutionMode())
        : null;

    DecisionInfo decisionInfo = row.getDecisionId() != null
        ? new DecisionInfo(
            row.getDecisionId(),
            row.getDecisionType(),
            row.getDecisionCurrentPrice(),
            row.getDecisionTargetPrice(),
            row.getDecisionExplanation(),
            row.getDecisionCreatedAt())
        : null;

    ActionInfo actionInfo = row.getActionId() != null
        ? new ActionInfo(
            row.getActionId(),
            row.getActionStatus(),
            row.getActionTargetPrice(),
            row.getActionExecutionMode(),
            row.getActionCreatedAt())
        : null;

    PromoInfo promoInfo = row.getPromoParticipationStatus() != null
        ? new PromoInfo(
            "PARTICIPATING".equals(row.getPromoParticipationStatus()),
            row.getPromoCampaignName(),
            row.getPromoPrice(),
            row.getPromoEndsAt())
        : null;

    LockInfo lockInfo = row.getLockId() != null
        ? new LockInfo(
            row.getLockedPrice(),
            row.getLockReason(),
            row.getLockedAt())
        : null;

    return new OfferDetailResponse(
        row.getOfferId(),
        row.getSkuCode(),
        row.getProductName(),
        row.getMarketplaceType(),
        row.getConnectionName(),
        row.getStatus(),
        row.getCategory(),
        row.getCurrentPrice(),
        row.getDiscountPrice(),
        row.getCostPrice(),
        row.getMarginPct(),
        row.getAvailableStock(),
        ch != null ? ch.getDaysOfCover() : null,
        ch != null ? ch.getStockRisk() : null,
        ch != null ? ch.getRevenue30d() : null,
        ch != null ? ch.getNetPnl30d() : null,
        ch != null ? ch.getVelocity14d() : null,
        ch != null ? ch.getReturnRatePct() : null,
        ch != null ? ch.getAdSpend30d() : null,
        ch != null ? ch.getDrr30dPct() : null,
        ch != null ? ch.getAdCpo() : null,
        ch != null ? ch.getAdRoas() : null,
        policyInfo,
        decisionInfo,
        actionInfo,
        promoInfo,
        lockInfo,
        row.getSimulatedPrice(),
        row.getSimulatedDeltaPct(),
        row.getLastSyncAt(),
        computeFreshness(row.getLastSyncAt())
    );
  }

  private String computeFreshness(OffsetDateTime lastSyncAt) {
    if (lastSyncAt == null) {
      return DataFreshness.STALE.name();
    }
    long hoursSinceSync = Duration.between(lastSyncAt, OffsetDateTime.now()).toHours();
    return hoursSinceSync > gridProperties.getFreshnessThresholdHours()
        ? DataFreshness.STALE.name()
        : DataFreshness.FRESH.name();
  }

  private ActionHistoryResponse toActionHistoryResponse(ActionHistoryRow row) {
    String reason = row.getCancelReason();
    if (reason == null) {
      reason = row.getHoldReason();
    }
    if (reason == null) {
      reason = row.getManualOverrideReason();
    }

    return new ActionHistoryResponse(
        row.getActionId(),
        row.getCreatedAt(),
        "PRICE_CHANGE",
        row.getStatus(),
        row.getTargetPrice(),
        row.getActualPrice(),
        row.getExecutionMode(),
        reason,
        null
    );
  }

  private Map<Long, ClickHouseEnrichment> fetchEnrichmentSafely(List<Long> offerIds) {
    return ChSafeQuery.getOrFallback(
        () -> chRepository.findEnrichment(offerIds),
        Map.of(), "enrichment/offerDetail");
  }
}
