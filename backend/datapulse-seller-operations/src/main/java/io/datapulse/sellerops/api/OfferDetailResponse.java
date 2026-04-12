package io.datapulse.sellerops.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record OfferDetailResponse(
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
        PolicyInfo activePolicy,
        DecisionInfo lastDecision,
        ActionInfo lastAction,
        PromoInfo promoStatus,
        LockInfo manualLock,
        BigDecimal simulatedPrice,
        BigDecimal simulatedDeltaPct,
        OffsetDateTime lastSyncAt,
        String dataFreshness
) {

    public record PolicyInfo(
            long policyId,
            String name,
            String strategyType,
            String executionMode
    ) {
    }

    public record DecisionInfo(
            long decisionId,
            String decisionType,
            BigDecimal currentPrice,
            BigDecimal targetPrice,
            String explanationSummary,
            OffsetDateTime createdAt
    ) {
    }

    public record ActionInfo(
            long actionId,
            String status,
            BigDecimal targetPrice,
            String executionMode,
            OffsetDateTime createdAt
    ) {
    }

    public record PromoInfo(
            boolean participating,
            String campaignName,
            BigDecimal promoPrice,
            OffsetDateTime endsAt
    ) {
    }

    public record LockInfo(
            BigDecimal lockedPrice,
            String reason,
            OffsetDateTime lockedAt
    ) {
    }
}
