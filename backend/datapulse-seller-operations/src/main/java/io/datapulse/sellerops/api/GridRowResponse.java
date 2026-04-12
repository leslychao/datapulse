package io.datapulse.sellerops.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record GridRowResponse(
        long offerId,
        long sellerSkuId,
        String skuCode,
        String productName,
        String marketplaceType,
        String connectionName,
        String status,
        String category,
        BigDecimal currentPrice,
        BigDecimal discountPrice,
        BigDecimal costPrice,
        BigDecimal marginPct,
        Integer availableStock,
        BigDecimal daysOfCover,
        String stockRisk,
        BigDecimal revenue30d,
        BigDecimal netPnl30d,
        BigDecimal velocity14d,
        BigDecimal returnRatePct,
        BigDecimal adSpend30d,
        BigDecimal drr30dPct,
        BigDecimal adCpo,
        BigDecimal adRoas,
        String activePolicy,
        String lastDecision,
        String lastActionStatus,
        Long pendingActionId,
        String promoStatus,
        boolean manualLock,
        BigDecimal simulatedPrice,
        BigDecimal simulatedDeltaPct,
        OffsetDateTime lastSyncAt,
        String dataFreshness,
        String bidPolicyName,
        String bidStrategyType,
        Integer currentBid,
        String lastBidDecisionType,
        boolean manualBidLock,
        Long bidLockId
) {
}
