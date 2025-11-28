package io.datapulse.etl.flow;

import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_ACCOUNT_ID;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_EVENT;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_RAW_TABLE;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_REQUEST_ID;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_SNAPSHOT_FILE;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_PROCESS_ID;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_SOURCE_ID;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_SOURCE_MARKETPLACE;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.dto.EtlSnapshotContext;
import java.nio.file.Path;
import org.springframework.messaging.MessageHeaders;
import org.springframework.stereotype.Component;

@Component
public final class EtlSnapshotContextExtractor {

  public EtlSnapshotContext extract(MessageHeaders headers) {
    String requestId = headers.get(HDR_ETL_REQUEST_ID, String.class);
    Long accountId = headers.get(HDR_ETL_ACCOUNT_ID, Long.class);
    String event = headers.get(HDR_ETL_EVENT, String.class);
    MarketplaceType marketplace =
        headers.get(HDR_ETL_SOURCE_MARKETPLACE, MarketplaceType.class);
    String sourceId = headers.get(HDR_ETL_SOURCE_ID, String.class);
    String snapshotId = headers.get(HDR_ETL_PROCESS_ID, String.class);
    Path snapshotFile = headers.get(HDR_ETL_SNAPSHOT_FILE, Path.class);
    String rawTable = headers.get(HDR_ETL_RAW_TABLE, String.class);

    return new EtlSnapshotContext(
        requestId,
        accountId,
        event,
        marketplace,
        sourceId,
        snapshotId,
        snapshotFile,
        rawTable
    );
  }
}
