package io.datapulse.etl.domain;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import io.datapulse.etl.config.IngestProperties;
import io.datapulse.etl.persistence.JobExecutionRepository;
import io.datapulse.etl.scheduling.StaleJobDetector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Eagerly applies the same staleness rules as {@link StaleJobDetector} for a single connection so
 * dispatch paths (manual sync, activation, scheduled sync, retry) are not blocked until the next
 * scheduled stale scan. Complements Rabbit-side orphan reclaim: that path needs a message delivery;
 * this path runs when the user or scheduler tries to create a new job.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConnectionStaleJobReconciler {

  private final JobExecutionRepository jobExecutionRepository;
  private final IngestProperties ingestProperties;
  private final Clock clock;

  /**
   * @return total rows updated to {@code STALE}
   */
  public int reconcileForDispatch(long connectionId) {
    OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    int n = jobExecutionRepository.markStaleInProgressForConnection(
        connectionId, now.minus(ingestProperties.jobTimeout()));
    n += jobExecutionRepository.markStaleMaterializingForConnection(
        connectionId, now.minus(ingestProperties.materializingStaleThreshold()));
    n += jobExecutionRepository.markStaleRetryScheduledForConnection(
        connectionId, now.minus(ingestProperties.staleRetryThreshold()));
    n += jobExecutionRepository.markStalePendingForConnection(
        connectionId, now.minus(ingestProperties.jobTimeout()));
    if (n > 0) {
      log.warn(
          "Stale jobs reconciled for connection before active-job check: connectionId={}, rows={}",
          connectionId,
          n);
    }
    return n;
  }
}
