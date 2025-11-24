package io.datapulse.etl.dto;

import io.datapulse.domain.MarketplaceType;
import java.nio.file.Path;

public record EtlSnapshotContext(
    String requestId,
    Long accountId,
    String event,
    MarketplaceType marketplace,
    String sourceId,
    String snapshotId,
    Path snapshotFile
) {

}
