package io.datapulse.sellerops.persistence;

import java.math.BigDecimal;

public record ClickHouseKpiRow(
    long criticalStockCount,
    BigDecimal revenue30dTotal,
    BigDecimal revenue30dTrend
) {
}
