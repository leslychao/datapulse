package io.datapulse.pricing.domain;

public record PolicyActivatedEvent(
    long policyId,
    long workspaceId
) {
}
