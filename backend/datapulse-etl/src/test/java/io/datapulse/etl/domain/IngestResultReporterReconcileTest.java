package io.datapulse.etl.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.etl.config.IngestProperties;
import io.datapulse.etl.config.IngestProperties.PostIngestMaterializationMode;
import io.datapulse.etl.persistence.JobExecutionRepository;
import io.datapulse.integration.domain.SyncStatus;
import io.datapulse.integration.persistence.MarketplaceSyncStateEntity;
import io.datapulse.integration.persistence.MarketplaceSyncStateRepository;
import io.datapulse.platform.outbox.OutboxService;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class IngestResultReporterReconcileTest {

  @Mock private OutboxService outboxService;
  @Mock private MarketplaceSyncStateRepository syncStateRepository;
  @Mock private JobExecutionRepository jobExecutionRepository;
  @Mock private ApplicationEventPublisher eventPublisher;

  @Captor private ArgumentCaptor<List<MarketplaceSyncStateEntity>> savedStatesCaptor;

  private IngestResultReporter reporter;

  @BeforeEach
  void setUp() {
    IngestProperties props =
        new IngestProperties(
            500,
            5000,
            Duration.ofHours(2),
            3,
            Duration.ofMinutes(5),
            Duration.ofMinutes(20),
            2,
            Duration.ofHours(1),
            Duration.ofHours(48),
            30,
            365,
            Duration.ofHours(1),
            Duration.ofMinutes(15),
            Duration.ofHours(1),
            PostIngestMaterializationMode.SYNC,
            Duration.ofMinutes(15));
    reporter =
        new IngestResultReporter(
            outboxService,
            syncStateRepository,
            jobExecutionRepository,
            new ObjectMapper(),
            props,
            eventPublisher);
  }

  @Test
  void reconcileSyncingWhenNoActiveJob_skipsWhenActiveJobExists() {
    when(jobExecutionRepository.existsActiveForConnection(1L)).thenReturn(true);

    reporter.reconcileSyncingWhenNoActiveJob(1L);

    verify(syncStateRepository, never()).findAllByMarketplaceConnectionId(any());
    verify(syncStateRepository, never()).saveAll(any());
  }

  @Test
  void reconcileSyncingWhenNoActiveJob_skipsWhenNothingSyncing() {
    when(jobExecutionRepository.existsActiveForConnection(1L)).thenReturn(false);
    MarketplaceSyncStateEntity idle = new MarketplaceSyncStateEntity();
    idle.setStatus(SyncStatus.IDLE.name());
    when(syncStateRepository.findAllByMarketplaceConnectionId(1L)).thenReturn(List.of(idle));

    reporter.reconcileSyncingWhenNoActiveJob(1L);

    verify(syncStateRepository, never()).saveAll(any());
  }

  @Test
  void reconcileSyncingWhenNoActiveJob_setsSyncingRowsToIdle() {
    when(jobExecutionRepository.existsActiveForConnection(1L)).thenReturn(false);
    MarketplaceSyncStateEntity syncing = new MarketplaceSyncStateEntity();
    syncing.setStatus(SyncStatus.SYNCING.name());
    syncing.setErrorMessage("x");
    MarketplaceSyncStateEntity idle = new MarketplaceSyncStateEntity();
    idle.setStatus(SyncStatus.IDLE.name());
    when(syncStateRepository.findAllByMarketplaceConnectionId(1L))
        .thenReturn(List.of(syncing, idle));

    reporter.reconcileSyncingWhenNoActiveJob(1L);

    verify(syncStateRepository).saveAll(savedStatesCaptor.capture());
    List<MarketplaceSyncStateEntity> saved = savedStatesCaptor.getValue();
    assertThat(saved).hasSize(2);
    assertThat(saved.stream().filter(s -> SyncStatus.IDLE.name().equals(s.getStatus())))
        .hasSize(2);
    assertThat(syncing.getErrorMessage()).isNull();
  }
}
