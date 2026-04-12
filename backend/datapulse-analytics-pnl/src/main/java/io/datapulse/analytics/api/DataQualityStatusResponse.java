package io.datapulse.analytics.api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record DataQualityStatusResponse(
    List<ConnectionDataQuality> connections
) {

  public record ConnectionDataQuality(
      String connectionName,
      String marketplaceType,
      boolean automationBlocked,
      String blockReason,
      Map<String, Object> blockReasonArgs,
      List<SyncDomainInfo> domains
  ) {}

  public record SyncDomainInfo(
      String domain,
      OffsetDateTime lastSuccessAt,
      String status
  ) {}
}
