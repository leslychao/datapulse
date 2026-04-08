package io.datapulse.execution.domain.event;

import io.datapulse.execution.domain.ActionExecutionMode;
import io.datapulse.execution.domain.ActionReconciliationSource;

import java.math.BigDecimal;

/**
 * Published when a price action succeeds (confirmed by reconciliation).
 * No listeners yet — intended for STOMP push (action list / grid refresh).
 */
public record ActionCompletedEvent(
        long actionId,
        long workspaceId,
        long marketplaceOfferId,
        ActionExecutionMode executionMode,
        BigDecimal targetPrice,
        ActionReconciliationSource reconciliationSource
) {
}
