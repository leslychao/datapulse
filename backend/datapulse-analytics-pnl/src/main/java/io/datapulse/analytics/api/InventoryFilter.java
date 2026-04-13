package io.datapulse.analytics.api;

public record InventoryFilter(
        String stockOutRisk,
        String sourcePlatform,
        String search,
        Long productId
) {}
