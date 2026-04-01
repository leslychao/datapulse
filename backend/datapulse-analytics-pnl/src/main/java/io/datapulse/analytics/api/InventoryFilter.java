package io.datapulse.analytics.api;

public record InventoryFilter(
        Long connectionId,
        String stockOutRisk,
        String search
) {}
