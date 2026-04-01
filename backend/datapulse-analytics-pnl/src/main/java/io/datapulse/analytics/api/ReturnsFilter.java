package io.datapulse.analytics.api;

public record ReturnsFilter(
        Long connectionId,
        Integer period,
        String search
) {}
