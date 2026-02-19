package io.datapulse.etl.v1.dto.scenario;

import io.datapulse.etl.MarketplaceEvent;

public record EtlEventCompletedEvent(
    String requestId,
    long accountId,
    MarketplaceEvent event
) {

}
