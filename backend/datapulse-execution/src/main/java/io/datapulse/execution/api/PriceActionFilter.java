package io.datapulse.execution.api;

import io.datapulse.execution.domain.ActionExecutionMode;
import io.datapulse.execution.domain.ActionStatus;

import java.time.LocalDate;

public record PriceActionFilter(
        Long connectionId,
        Long marketplaceOfferId,
        ActionStatus status,
        ActionExecutionMode executionMode,
        LocalDate from,
        LocalDate to
) {
}
