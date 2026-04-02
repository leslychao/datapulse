package io.datapulse.pricing.domain;

public record PolicyLogicChangedEvent(
    long policyId,
    long workspaceId,
    int newVersion
) {
}
