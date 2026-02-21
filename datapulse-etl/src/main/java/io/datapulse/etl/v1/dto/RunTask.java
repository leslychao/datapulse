package io.datapulse.etl.v1.dto;

import io.datapulse.etl.MarketplaceEvent;
import java.time.LocalDate;

public record RunTask(
    Long accountId,
    MarketplaceEvent event,
    LocalDate dateFrom,
    LocalDate dateTo
) {
}
