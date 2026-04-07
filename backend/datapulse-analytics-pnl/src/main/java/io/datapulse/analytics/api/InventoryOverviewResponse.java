package io.datapulse.analytics.api;

import java.math.BigDecimal;
import java.util.List;

public record InventoryOverviewResponse(
        int totalSkus,
        int criticalCount,
        int warningCount,
        int normalCount,
        BigDecimal frozenCapital,
        List<ProductInventoryResponse> topCritical
) {}
