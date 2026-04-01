package io.datapulse.sellerops.api;

import java.math.BigDecimal;

public record GridKpiResponse(
    long totalOffers,
    BigDecimal avgMarginPct,
    BigDecimal avgMarginTrend,
    long pendingActionsCount,
    long criticalStockCount,
    BigDecimal revenue30dTotal,
    BigDecimal revenue30dTrend
) {
}
