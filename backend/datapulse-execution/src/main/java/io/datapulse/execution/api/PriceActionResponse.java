package io.datapulse.execution.api;

import io.datapulse.execution.domain.ActionExecutionMode;
import io.datapulse.execution.domain.ActionReconciliationSource;
import io.datapulse.execution.domain.ActionStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PriceActionResponse(
        long id,
        long workspaceId,
        long marketplaceOfferId,
        long priceDecisionId,
        ActionExecutionMode executionMode,
        ActionStatus status,
        BigDecimal targetPrice,
        BigDecimal currentPriceAtCreation,
        Long approvedByUserId,
        OffsetDateTime approvedAt,
        String holdReason,
        String cancelReason,
        Long supersededByActionId,
        ActionReconciliationSource reconciliationSource,
        String manualOverrideReason,
        int attemptCount,
        int maxAttempts,
        int approvalTimeoutHours,
        OffsetDateTime nextAttemptAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
