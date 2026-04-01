package io.datapulse.execution.persistence;

import io.datapulse.execution.domain.ActionExecutionMode;
import io.datapulse.execution.domain.ActionStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PriceActionSummaryRow(
        long id,
        long marketplaceOfferId,
        ActionExecutionMode executionMode,
        ActionStatus status,
        BigDecimal targetPrice,
        BigDecimal currentPriceAtCreation,
        int attemptCount,
        int maxAttempts,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
