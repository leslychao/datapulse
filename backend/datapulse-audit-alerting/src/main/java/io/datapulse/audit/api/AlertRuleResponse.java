package io.datapulse.audit.api;

import java.time.OffsetDateTime;

public record AlertRuleResponse(
        long id,
        long workspaceId,
        String ruleType,
        String targetEntityType,
        Long targetEntityId,
        Object config,
        boolean enabled,
        String severity,
        boolean blocksAutomation,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
