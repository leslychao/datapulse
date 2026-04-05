package io.datapulse.etl.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.datapulse.etl.config.IngestProperties;
import io.datapulse.etl.config.IngestProperties.PostIngestMaterializationMode;
import io.datapulse.etl.persistence.JobExecutionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConnectionStaleJobReconcilerTest {

  private static final ZoneOffset UTC = ZoneOffset.UTC;

  @Mock private JobExecutionRepository jobExecutionRepository;
  @Mock private IngestResultReporter ingestResultReporter;

  private ConnectionStaleJobReconciler reconciler;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(Instant.parse("2024-06-15T12:00:00Z"), UTC);
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
            Duration.ofHours(6),
            Duration.ofMinutes(15),
            Duration.ofHours(1),
            PostIngestMaterializationMode.SYNC,
            Duration.ofMinutes(15));
    reconciler =
        new ConnectionStaleJobReconciler(
            jobExecutionRepository, props, ingestResultReporter, clock);
  }

  @Test
  void reconcileForDispatch_usesSameThresholdsAsStaleDetector() {
    when(jobExecutionRepository.markStaleInProgressForConnection(7L, OffsetDateTime.parse("2024-06-15T10:00:00Z")))
        .thenReturn(1);
    when(jobExecutionRepository.markStaleMaterializingForConnection(7L, OffsetDateTime.parse("2024-06-15T11:00:00Z")))
        .thenReturn(0);
    when(jobExecutionRepository.markStaleRetryScheduledForConnection(7L, OffsetDateTime.parse("2024-06-15T11:00:00Z")))
        .thenReturn(0);
    when(jobExecutionRepository.markStalePendingForConnection(7L, OffsetDateTime.parse("2024-06-15T10:00:00Z")))
        .thenReturn(0);

    int total = reconciler.reconcileForDispatch(7L);

    assertThat(total).isEqualTo(1);
    verify(jobExecutionRepository).markStaleInProgressForConnection(7L, OffsetDateTime.parse("2024-06-15T10:00:00Z"));
    verify(jobExecutionRepository)
        .markStaleMaterializingForConnection(7L, OffsetDateTime.parse("2024-06-15T11:00:00Z"));
    verify(jobExecutionRepository)
        .markStaleRetryScheduledForConnection(7L, OffsetDateTime.parse("2024-06-15T11:00:00Z"));
    verify(jobExecutionRepository).markStalePendingForConnection(7L, OffsetDateTime.parse("2024-06-15T10:00:00Z"));
    verify(ingestResultReporter).reconcileSyncingWhenNoActiveJob(7L);
  }
}
