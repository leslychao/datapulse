package io.datapulse.etl.dto.scenario;

import io.datapulse.etl.MarketplaceEvent;

public record EtlEventCompletedEvent(
    String requestId,
    long accountId,
    MarketplaceEvent event
) {

}
