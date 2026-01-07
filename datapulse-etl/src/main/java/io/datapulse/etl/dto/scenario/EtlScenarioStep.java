package io.datapulse.etl.dto.scenario;

import io.datapulse.etl.MarketplaceEvent;
import java.time.LocalDate;

public record EtlScenarioStep(
    String requestId,
    long accountId,
    MarketplaceEvent event,
    LocalDate dateFrom,
    LocalDate dateTo
) {

}
