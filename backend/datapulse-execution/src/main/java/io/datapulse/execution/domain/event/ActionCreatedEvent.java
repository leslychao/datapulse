package io.datapulse.execution.domain.event;

import io.datapulse.execution.domain.ActionExecutionMode;
import io.datapulse.execution.domain.ActionStatus;

import java.math.BigDecimal;

/**
 * Published when a new price action is created.
 * No listeners yet — intended for STOMP push (action list refresh) and approval request notification.
 */
public record ActionCreatedEvent(
        long actionId,
        long workspaceId,
        long marketplaceOfferId,
        long priceDecisionId,
        ActionExecutionMode executionMode,
        ActionStatus initialStatus,
        BigDecimal targetPrice,
        BigDecimal currentPrice
) {
}
