package io.datapulse.integration.domain.event;

public record ConnectionCreatedEvent(
        Long connectionId,
        Long workspaceId,
        String marketplaceType,
        Long userId
) {
}
