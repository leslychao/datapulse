package io.datapulse.analytics.api;

import java.time.OffsetDateTime;
import java.util.List;

public record DataQualityStatusResponse(
    List<ConnectionDataQuality> connections
) {

  public record ConnectionDataQuality(
      long connectionId,
      String connectionName,
      String marketplaceType,
      boolean automationBlocked,
      String blockReason,
      List<SyncDomainInfo> domains
  ) {}

  public record SyncDomainInfo(
      String domain,
      OffsetDateTime lastSuccessAt,
      String status,
      long recordCount
  ) {}
}
