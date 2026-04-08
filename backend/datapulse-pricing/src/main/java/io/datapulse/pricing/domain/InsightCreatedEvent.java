package io.datapulse.pricing.domain;

/** Published when a pricing insight is created. No listeners yet. */
public record InsightCreatedEvent(
    long insightId,
    long workspaceId,
    String insightType,
    String title,
    String severity
) {
}
