package io.datapulse.etl.v1.dto;

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
