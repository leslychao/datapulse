package io.datapulse.etl.dto;

import io.datapulse.etl.MarketplaceEvent;
import java.time.LocalDate;

public record OrchestrationCommand(
    String requestId,
    Long accountId,
    MarketplaceEvent event,
    LocalDate from,
    LocalDate to
) {

}
