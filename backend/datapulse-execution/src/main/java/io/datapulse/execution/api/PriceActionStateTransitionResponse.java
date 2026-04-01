package io.datapulse.execution.api;

import io.datapulse.execution.domain.ActionStatus;

import java.time.OffsetDateTime;

public record PriceActionStateTransitionResponse(
    ActionStatus fromStatus,
    ActionStatus toStatus,
    OffsetDateTime timestamp,
    String actor,
    String reason
) {
}
