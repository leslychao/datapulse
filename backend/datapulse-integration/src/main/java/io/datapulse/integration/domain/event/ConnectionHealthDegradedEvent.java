package io.datapulse.integration.domain.event;

public record ConnectionHealthDegradedEvent(
        Long connectionId,
        Long workspaceId,
        String marketplaceType,
        int consecutiveFailures,
        String lastErrorCode
) {
}
