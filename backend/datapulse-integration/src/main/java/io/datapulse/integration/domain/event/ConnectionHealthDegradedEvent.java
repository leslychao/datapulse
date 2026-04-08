package io.datapulse.integration.domain.event;

/**
 * Published when a connection's health probe fails N consecutive times, degrading to AUTH_FAILED.
 * No listeners yet — intended for WARNING alert.
 */
public record ConnectionHealthDegradedEvent(
        Long connectionId,
        Long workspaceId,
        String marketplaceType,
        int consecutiveFailures,
        String lastErrorCode
) {
}
