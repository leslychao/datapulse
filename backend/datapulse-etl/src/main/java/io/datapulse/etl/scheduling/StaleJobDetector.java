package io.datapulse.etl.scheduling;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import io.datapulse.etl.config.IngestProperties;
import io.datapulse.etl.domain.IngestResultReporter;
import io.datapulse.etl.persistence.JobExecutionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Detects stale ETL jobs that are stuck in non-terminal states.
 *
 * <p>Conditions:
 * <ol>
 *   <li>{@code IN_PROGRESS} for longer than {@code jobTimeout} (default 2h) — worker crash or hang</li>
 *   <li>{@code MATERIALIZING} for longer than {@code materializingStaleThreshold} (default 1h) — post-DAG
 *       materialization stuck (e.g. lost {@code ETL_POST_INGEST_MATERIALIZE} message)</li>
 *   <li>{@code RETRY_SCHEDULED} for longer than {@code staleRetryThreshold} (default 1h) — DLX message lost</li>
 * </ol>
 *
 * <p>STALE is a terminal status. Recovery: next scheduled sync creates a new job_execution
 * (concurrency guard excludes STALE jobs).
 *
 * <p>On startup ({@link ApplicationReadyEvent}), runs the same detection with a shorter threshold
 * for {@code RETRY_SCHEDULED}: {@link IngestProperties#maxRetryBackoff()} instead of
 * {@link IngestProperties#staleRetryThreshold()}. After a restart, if a retry message has not
 * arrived within {@code maxRetryBackoff}, it is likely lost by the broker.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StaleJobDetector {

  private final JobExecutionRepository jobExecutionRepository;
  private final IngestProperties ingestProperties;
  private final IngestResultReporter ingestResultReporter;
  private final Clock clock;

  @EventListener(ApplicationReadyEvent.class)
  public void reconcileOnStartup() {
    try {
      OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);

      int staleInProgress =
          jobExecutionRepository.markStaleInProgress(now.minus(ingestProperties.jobTimeout()));
      int staleMaterializing = jobExecutionRepository.markStaleMaterializing(
          now.minus(ingestProperties.materializingStaleThreshold()));
      int staleRetryScheduled = jobExecutionRepository.markStaleRetryScheduled(
          now.minus(ingestProperties.maxRetryBackoff()));

      int total = staleInProgress + staleMaterializing + staleRetryScheduled;
      if (total > 0) {
        log.warn(
            "Startup stale-job reconciliation: inProgress={}, materializing={}, retryScheduled={}",
            staleInProgress,
            staleMaterializing,
            staleRetryScheduled);
      }
      ingestResultReporter.reconcileAllConnectionsStuckInSyncingWithoutActiveJob();
      log.info("Startup stale-job reconciliation complete: markedStale={}", total);
    } catch (Exception e) {
      log.error("Startup stale-job reconciliation failed", e);
    }
  }

  @Scheduled(fixedDelayString = "${datapulse.etl.stale-check-interval:PT15M}")
  @SchedulerLock(name = "staleJobDetector", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
  public void detectStaleJobs() {
    try {
      OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
      Duration jobTimeout = ingestProperties.jobTimeout();
      Duration matThreshold = ingestProperties.materializingStaleThreshold();
      Duration retryThreshold = ingestProperties.staleRetryThreshold();

      int staleInProgress = jobExecutionRepository.markStaleInProgress(now.minus(jobTimeout));
      int staleMaterializing =
          jobExecutionRepository.markStaleMaterializing(now.minus(matThreshold));
      int staleRetryScheduled =
          jobExecutionRepository.markStaleRetryScheduled(now.minus(retryThreshold));

      if (staleInProgress > 0 || staleMaterializing > 0 || staleRetryScheduled > 0) {
        log.warn(
            "Stale jobs detected: inProgress={}, materializing={}, retryScheduled={}",
            staleInProgress,
            staleMaterializing,
            staleRetryScheduled);
      } else {
        log.debug("No stale jobs detected");
      }
      ingestResultReporter.reconcileAllConnectionsStuckInSyncingWithoutActiveJob();
    } catch (Exception e) {
      log.error("Stale job detection failed", e);
    }
  }
}
