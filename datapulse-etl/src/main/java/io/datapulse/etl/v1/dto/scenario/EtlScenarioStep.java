package io.datapulse.etl.v1.dto.scenario;

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
