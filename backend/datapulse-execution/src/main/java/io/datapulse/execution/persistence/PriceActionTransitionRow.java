package io.datapulse.execution.persistence;

import io.datapulse.execution.domain.ActionStatus;

import java.time.OffsetDateTime;

public record PriceActionTransitionRow(
    ActionStatus fromStatus,
    ActionStatus toStatus,
    OffsetDateTime createdAt,
    String actorName,
    String reason
) {
}
