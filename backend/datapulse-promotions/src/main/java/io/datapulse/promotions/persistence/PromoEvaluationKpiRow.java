package io.datapulse.promotions.persistence;

public record PromoEvaluationKpiRow(
    long total,
    long profitableCount,
    long marginalCount,
    long unprofitableCount) {}
