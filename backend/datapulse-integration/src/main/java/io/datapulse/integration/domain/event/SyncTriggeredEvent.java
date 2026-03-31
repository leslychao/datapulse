package io.datapulse.integration.domain.event;

public record SyncTriggeredEvent(
        Long connectionId,
        Long workspaceId,
        Long userId
) {}
