package io.datapulse.execution.api;

import io.datapulse.execution.domain.ActionExecutionMode;
import io.datapulse.execution.domain.ActionStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record PriceActionDetailResponse(
    long id,
    String type,
    String offerName,
    long offerId,
    String sku,
    String marketplace,
    String connectionName,
    ActionStatus status,
    ActionExecutionMode executionMode,
    BigDecimal targetPrice,
    BigDecimal currentPriceAtCreation,
    BigDecimal priceDeltaPct,
    int attemptCount,
    int maxAttempts,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    String initiatedBy,
    String approvedBy,
    OffsetDateTime approvedAt,
    String holdReason,
    String cancelReason,
    String lastErrorMessage,
    Long supersedingActionId,
    List<PriceActionAttemptResponse> attempts,
    List<PriceActionStateTransitionResponse> stateHistory
) {
}
