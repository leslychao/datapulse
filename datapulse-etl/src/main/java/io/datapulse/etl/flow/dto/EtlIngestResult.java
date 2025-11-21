package io.datapulse.etl.flow.dto;

import io.datapulse.domain.MarketplaceType;

public record EtlIngestResult(
    String snapshotId,
    MarketplaceType marketplace,
    String sourceId,
    long totalElements
) {

}
