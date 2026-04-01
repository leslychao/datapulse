package io.datapulse.execution.domain;

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
