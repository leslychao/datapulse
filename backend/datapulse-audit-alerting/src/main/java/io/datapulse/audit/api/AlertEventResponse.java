package io.datapulse.audit.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.OffsetDateTime;

public record AlertEventResponse(
        long id,
        Long alertRuleId,
        long workspaceId,
        String sourcePlatform,
        @JsonIgnore Long connectionId,
        String status,
        String severity,
        String title,
        String details,
        boolean blocksAutomation,
        OffsetDateTime openedAt,
        OffsetDateTime acknowledgedAt,
        Long acknowledgedBy,
        OffsetDateTime resolvedAt,
        String resolvedReason
) {
}
