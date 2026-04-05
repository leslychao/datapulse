package io.datapulse.etl.scheduling;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.datapulse.etl.config.IngestProperties;
import io.datapulse.etl.domain.IngestResultReporter;
import io.datapulse.etl.persistence.JobExecutionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StaleJobDetectorTest {

  @Mock private JobExecutionRepository jobExecutionRepository;
  @Mock private IngestProperties ingestProperties;
  @Mock private IngestResultReporter ingestResultReporter;

  private StaleJobDetector staleJobDetector;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(Instant.parse("2024-06-15T12:00:00Z"), ZoneOffset.UTC);
    staleJobDetector =
        new StaleJobDetector(jobExecutionRepository, ingestProperties, ingestResultReporter, clock);
  }

  @Test
  void should_runAllStaleStrategies_when_detectStaleJobs() {
    when(ingestProperties.jobTimeout()).thenReturn(Duration.ofHours(2));
    when(ingestProperties.materializingStaleThreshold()).thenReturn(Duration.ofMinutes(45));
    when(ingestProperties.staleRetryThreshold()).thenReturn(Duration.ofHours(1));
    when(jobExecutionRepository.markStaleInProgress(any())).thenReturn(0);
    when(jobExecutionRepository.markStaleMaterializing(any())).thenReturn(0);
    when(jobExecutionRepository.markStaleRetryScheduled(any())).thenReturn(0);

    staleJobDetector.detectStaleJobs();

    verify(jobExecutionRepository).markStaleInProgress(any());
    verify(jobExecutionRepository).markStaleMaterializing(any());
    verify(jobExecutionRepository).markStaleRetryScheduled(any());
    verify(ingestResultReporter).reconcileAllConnectionsStuckInSyncingWithoutActiveJob();
  }
}
