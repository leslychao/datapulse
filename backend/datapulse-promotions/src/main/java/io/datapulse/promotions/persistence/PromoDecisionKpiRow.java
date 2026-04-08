package io.datapulse.promotions.persistence;

public record PromoDecisionKpiRow(
    long participateCount,
    long declineCount,
    long pendingReviewCount) {}
