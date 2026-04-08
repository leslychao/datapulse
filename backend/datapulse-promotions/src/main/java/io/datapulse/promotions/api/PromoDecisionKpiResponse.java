package io.datapulse.promotions.api;

public record PromoDecisionKpiResponse(
    long participateCount,
    long declineCount,
    long pendingReviewCount) {}
