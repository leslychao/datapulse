package io.datapulse.execution.domain.event;

import io.datapulse.execution.domain.ActionExecutionMode;
import io.datapulse.execution.domain.ActionReconciliationSource;

import java.math.BigDecimal;

public record ActionCompletedEvent(
        long actionId,
        long workspaceId,
        long marketplaceOfferId,
        ActionExecutionMode executionMode,
        BigDecimal targetPrice,
        ActionReconciliationSource reconciliationSource
) {
}
