package io.datapulse.execution.domain.event;

import io.datapulse.execution.domain.ActionExecutionMode;

import java.math.BigDecimal;

public record ActionCreatedEvent(
        long actionId,
        long workspaceId,
        long marketplaceOfferId,
        long priceDecisionId,
        ActionExecutionMode executionMode,
        BigDecimal targetPrice,
        BigDecimal currentPrice
) {
}
