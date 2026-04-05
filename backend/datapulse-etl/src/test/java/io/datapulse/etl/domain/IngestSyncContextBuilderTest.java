package io.datapulse.etl.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.etl.config.IngestProperties;
import io.datapulse.etl.config.IngestProperties.PostIngestMaterializationMode;
import io.datapulse.etl.persistence.JobExecutionRow;
import io.datapulse.integration.domain.MarketplaceType;
import io.datapulse.integration.persistence.MarketplaceSyncStateEntity;
import io.datapulse.integration.persistence.MarketplaceSyncStateRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IngestSyncContextBuilderTest {

  private static final ZoneOffset TZ = ZoneOffset.UTC;

  @Mock private CredentialResolver credentialResolver;
  @Mock private CheckpointManager checkpointManager;
  @Mock private MarketplaceSyncStateRepository syncStateRepository;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final Clock clock =
      Clock.fixed(Instant.parse("2024-06-15T12:00:00Z"), TZ);
  private final IngestProperties ingestProperties = new IngestProperties(
      500, 5000, Duration.ofHours(2), 3,
      Duration.ofMinutes(5), Duration.ofMinutes(20), 2,
      Duration.ofHours(1), Duration.ofHours(48),
      30,
      365,
      Duration.ofHours(1),
      Duration.ofMinutes(15),
      Duration.ofHours(1),
      PostIngestMaterializationMode.SYNC,
      Duration.ofMinutes(15));

  private IngestSyncContextBuilder builder;

  @BeforeEach
  void setUp() {
    builder = new IngestSyncContextBuilder(
        credentialResolver,
        checkpointManager,
        objectMapper,
        syncStateRepository,
        ingestProperties,
        clock);
    when(credentialResolver.resolve(anyLong()))
        .thenReturn(new CredentialResolver.ResolvedCredentials(
            2L, 10L, MarketplaceType.WB, Map.of("apiToken", "t")));
    when(checkpointManager.parse(any())).thenReturn(Map.of());
    lenient().when(syncStateRepository.findAllByMarketplaceConnectionId(anyLong()))
        .thenReturn(List.of());
  }

  @Test
  void should_expandManualDomainsWithHardDependencies() {
    JobExecutionRow job = JobExecutionRow.builder()
        .id(1L)
        .connectionId(2L)
        .eventType("MANUAL_SYNC")
        .params("{\"domains\":[\"SALES_FACT\"]}")
        .build();

    IngestContext ctx = builder.build(job);

    assertThat(ctx.scope()).containsExactlyInAnyOrder(
        EtlEventType.SALES_FACT,
        EtlEventType.PRODUCT_DICT,
        EtlEventType.CATEGORY_DICT,
        EtlEventType.WAREHOUSE_DICT);
  }

  @Test
  void should_useFullDagScope_when_manualSyncHasNoDomains() {
    JobExecutionRow job = JobExecutionRow.builder()
        .id(1L)
        .connectionId(2L)
        .eventType("MANUAL_SYNC")
        .params(null)
        .build();

    assertThat(builder.build(job).scope()).isEqualTo(DagDefinition.fullSyncScope());
  }

  @Test
  void should_useFullDagScope_forFullSync() {
    JobExecutionRow job = JobExecutionRow.builder()
        .id(1L)
        .connectionId(2L)
        .eventType("FULL_SYNC")
        .build();

    assertThat(builder.build(job).scope()).isEqualTo(DagDefinition.fullSyncScope());
  }

  @Test
  void should_useFullDagScope_forIncremental() {
    JobExecutionRow job = JobExecutionRow.builder()
        .id(1L)
        .connectionId(2L)
        .eventType("INCREMENTAL")
        .build();

    assertThat(builder.build(job).scope()).isEqualTo(DagDefinition.fullSyncScope());
  }

  @Test
  void should_useFullFactLookback_forFullSync() {
    JobExecutionRow job = JobExecutionRow.builder()
        .id(1L)
        .connectionId(2L)
        .eventType("FULL_SYNC")
        .build();

    IngestContext ctx = builder.build(job);

    assertThat(ctx.wbFactDateFrom()).isEqualTo(LocalDate.of(2023, 6, 16));
    assertThat(ctx.wbFactDateTo()).isEqualTo(LocalDate.of(2024, 6, 15));
    assertThat(ctx.ozonFactSince()).isEqualTo(OffsetDateTime.parse("2023-06-16T12:00:00Z"));
    assertThat(ctx.ozonFactTo()).isEqualTo(OffsetDateTime.parse("2024-06-15T12:00:00Z"));
  }

  @Test
  void should_useWatermark_when_incrementalAndLastSuccessPresent() {
    var state = new MarketplaceSyncStateEntity();
    state.setLastSuccessAt(OffsetDateTime.parse("2024-06-14T10:00:00Z"));
    when(syncStateRepository.findAllByMarketplaceConnectionId(2L))
        .thenReturn(List.of(state));

    JobExecutionRow job = JobExecutionRow.builder()
        .id(1L)
        .connectionId(2L)
        .eventType("INCREMENTAL")
        .build();

    IngestContext ctx = builder.build(job);

    assertThat(ctx.ozonFactSince()).isEqualTo(OffsetDateTime.parse("2024-06-14T09:00:00Z"));
    assertThat(ctx.wbFactDateFrom()).isEqualTo(LocalDate.of(2024, 6, 14));
  }

  @Test
  void should_capIncrementalStart_atIncrementalHorizon_when_watermarkTooOld() {
    var state = new MarketplaceSyncStateEntity();
    state.setLastSuccessAt(OffsetDateTime.parse("2020-01-01T00:00:00Z"));
    when(syncStateRepository.findAllByMarketplaceConnectionId(2L))
        .thenReturn(List.of(state));

    JobExecutionRow job = JobExecutionRow.builder()
        .id(1L)
        .connectionId(2L)
        .eventType("INCREMENTAL")
        .build();

    IngestContext ctx = builder.build(job);

    assertThat(ctx.ozonFactSince()).isEqualTo(OffsetDateTime.parse("2024-05-16T12:00:00Z"));
  }

  @Test
  void should_useFullLookback_when_incrementalButNoLastSuccess() {
    JobExecutionRow job = JobExecutionRow.builder()
        .id(1L)
        .connectionId(2L)
        .eventType("INCREMENTAL")
        .build();

    IngestContext ctx = builder.build(job);

    assertThat(ctx.wbFactDateFrom()).isEqualTo(LocalDate.of(2023, 6, 16));
  }
}
