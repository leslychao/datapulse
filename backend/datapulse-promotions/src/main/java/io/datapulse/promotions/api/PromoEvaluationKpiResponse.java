package io.datapulse.promotions.api;

public record PromoEvaluationKpiResponse(
    long total,
    long profitableCount,
    long marginalCount,
    long unprofitableCount) {}
