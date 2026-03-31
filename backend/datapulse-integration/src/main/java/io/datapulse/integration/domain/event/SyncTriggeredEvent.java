package io.datapulse.integration.domain.event;

import java.util.List;

public record SyncTriggeredEvent(
        Long connectionId,
        Long workspaceId,
        Long userId,
        List<String> domains
) {}
