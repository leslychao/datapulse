package io.datapulse.etl.dto.scenario;

import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.dto.EtlDateMode;
import java.time.LocalDate;

public record EtlScenarioEventConfig(
    MarketplaceEvent event,
    EtlDateMode dateMode,
    LocalDate dateFrom,
    LocalDate dateTo,
    Integer lastDays
) {

}
