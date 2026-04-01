package io.datapulse.analytics.api;

import java.math.BigDecimal;

public record InventoryOverviewResponse(
        int totalSkus,
        int criticalRiskCount,
        int warningRiskCount,
        int normalRiskCount,
        BigDecimal totalFrozenCapital
) {}
