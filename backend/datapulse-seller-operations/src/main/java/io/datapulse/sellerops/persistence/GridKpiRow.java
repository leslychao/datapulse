package io.datapulse.sellerops.persistence;

import java.math.BigDecimal;

public record GridKpiRow(
    long totalOffers,
    BigDecimal avgMarginPct,
    long pendingActionsCount
) {
}
