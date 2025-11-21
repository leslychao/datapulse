package io.datapulse.etl.flow.dto;

import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_ACCOUNT_ID;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_EVENT;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_REQUEST_ID;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_SOURCE_ID;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_SOURCE_MP;

import io.datapulse.domain.MarketplaceEvent;
import io.datapulse.domain.MarketplaceType;
import org.springframework.messaging.MessageHeaders;

public record EarlySourceFailureContext(
    String requestId,
    Long accountId,
    MarketplaceEvent event,
    MarketplaceType marketplace,
    String sourceId
) {

  public static EarlySourceFailureContext fromHeaders(MessageHeaders h) {
    String requestId = h.get(HDR_ETL_REQUEST_ID, String.class);
    Long accountId = h.get(HDR_ETL_ACCOUNT_ID, Long.class);
    String eventValue = h.get(HDR_ETL_EVENT, String.class);
    MarketplaceEvent event = eventValue != null ? MarketplaceEvent.fromString(eventValue) : null;
    MarketplaceType marketplace = h.get(HDR_ETL_SOURCE_MP, MarketplaceType.class);
    String sourceId = h.get(HDR_ETL_SOURCE_ID, String.class);
    return new EarlySourceFailureContext(requestId, accountId, event, marketplace, sourceId);
  }

  public boolean isComplete() {
    return requestId != null
        && accountId != null
        && event != null
        && marketplace != null
        && sourceId != null;
  }
}
