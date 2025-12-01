package io.datapulse.etl.flow;

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
