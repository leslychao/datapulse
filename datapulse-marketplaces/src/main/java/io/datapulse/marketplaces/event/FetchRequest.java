package io.datapulse.marketplaces.event;

import java.time.LocalDate;

public record FetchRequest(
    long accountId,
    MarketplaceEvent event,
    LocalDate from,
    LocalDate to,
    FetchParams params
) {

}
