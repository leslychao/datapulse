package io.datapulse.etl.domain;

import io.datapulse.integration.domain.MarketplaceType;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;

/**
 * Stable fact-window values for unit tests that do not exercise {@link IngestSyncContextBuilder}.
 */
public final class IngestContextFixtures {

  public static final LocalDate WB_FACT_DATE_FROM = LocalDate.of(2024, 1, 1);
  public static final LocalDate WB_FACT_DATE_TO = LocalDate.of(2024, 6, 1);
  public static final OffsetDateTime OZON_FACT_SINCE = OffsetDateTime.parse("2024-01-01T00:00:00Z");
  public static final OffsetDateTime OZON_FACT_TO = OffsetDateTime.parse("2024-06-01T00:00:00Z");

  private IngestContextFixtures() {}

  public static IngestContext any(
      long jobExecutionId,
      long connectionId,
      long workspaceId,
      MarketplaceType marketplace,
      Map<String, String> credentials,
      String eventType,
      Set<EtlEventType> scope,
      Map<EtlEventType, IngestContext.CheckpointEntry> checkpoint) {
    return new IngestContext(
        jobExecutionId,
        connectionId,
        workspaceId,
        marketplace,
        credentials,
        eventType,
        scope,
        checkpoint,
        WB_FACT_DATE_FROM,
        WB_FACT_DATE_TO,
        OZON_FACT_SINCE,
        OZON_FACT_TO);
  }
}
