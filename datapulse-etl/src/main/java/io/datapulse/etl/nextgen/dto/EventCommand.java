package io.datapulse.etl.nextgen.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record EventCommand(
    String eventId,
    Long accountId,
    String eventType,
    OffsetDateTime from,
    OffsetDateTime to,
    List<MarketplaceScope> marketplaces
) {
}
