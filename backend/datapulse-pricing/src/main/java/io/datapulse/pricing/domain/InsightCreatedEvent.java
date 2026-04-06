package io.datapulse.pricing.domain;

public record InsightCreatedEvent(
    long insightId,
    long workspaceId,
    String insightType,
    String title,
    String severity
) {
}
