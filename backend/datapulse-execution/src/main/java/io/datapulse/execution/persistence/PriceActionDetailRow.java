package io.datapulse.execution.persistence;

import io.datapulse.execution.domain.ActionExecutionMode;
import io.datapulse.execution.domain.ActionStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PriceActionDetailRow(
    long id,
    long marketplaceOfferId,
    String offerName,
    String sku,
    String marketplace,
    String connectionName,
    ActionExecutionMode executionMode,
    ActionStatus status,
    BigDecimal targetPrice,
    BigDecimal currentPriceAtCreation,
    Long approvedByUserId,
    String approvedByName,
    OffsetDateTime approvedAt,
    String holdReason,
    String cancelReason,
    Long supersededByActionId,
    int attemptCount,
    int maxAttempts,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
