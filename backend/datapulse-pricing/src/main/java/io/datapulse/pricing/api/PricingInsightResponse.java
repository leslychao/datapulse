package io.datapulse.pricing.api;

import java.time.OffsetDateTime;

public record PricingInsightResponse(
    long id,
    long workspaceId,
    String insightType,
    String title,
    String body,
    String severity,
    boolean acknowledged,
    OffsetDateTime createdAt
) {
}
