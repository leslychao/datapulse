package io.datapulse.execution.domain;

import java.time.LocalDate;

public record PriceActionFilter(
        String sourcePlatform,
        Long marketplaceOfferId,
        ActionStatus status,
        ActionExecutionMode executionMode,
        LocalDate from,
        LocalDate to
) {
}
