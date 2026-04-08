package io.datapulse.execution.domain.event;

import io.datapulse.execution.domain.ActionExecutionMode;
import io.datapulse.execution.domain.ErrorClassification;

import java.math.BigDecimal;

/**
 * Published when a price action terminally fails (max attempts exhausted or non-retriable error).
 * Consumed by: {@link io.datapulse.execution.domain.ActionAlertListener} (CRITICAL alert).
 */
public record ActionFailedEvent(
        long actionId,
        long workspaceId,
        long marketplaceOfferId,
        ActionExecutionMode executionMode,
        BigDecimal targetPrice,
        int attemptCount,
        ErrorClassification lastErrorClassification,
        String lastErrorMessage
) {
}
