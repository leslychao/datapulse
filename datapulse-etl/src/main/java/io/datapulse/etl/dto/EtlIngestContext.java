package io.datapulse.etl.dto;

import io.datapulse.domain.MarketplaceType;
import java.nio.file.Path;

public record EtlIngestContext(
    String requestId,
    Long accountId,
    String event,
    MarketplaceType marketplace,
    String sourceId,
    Path snapshotFile,
    String rawTable
) {

}
