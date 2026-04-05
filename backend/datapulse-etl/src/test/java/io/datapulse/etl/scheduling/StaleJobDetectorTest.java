package io.datapulse.etl.scheduling;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.datapulse.etl.config.IngestProperties;
import io.datapulse.etl.domain.IngestResultReporter;
import io.datapulse.etl.persistence.JobExecutionRepository;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StaleJobDetectorTest {

  @Mock private JobExecutionRepository jobExecutionRepository;
  @Mock private IngestProperties ingestProperties;
  @Mock private IngestResultReporter ingestResultReporter;

  @InjectMocks private StaleJobDetector staleJobDetector;

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
