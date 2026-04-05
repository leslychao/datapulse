package io.datapulse.etl.domain;

import io.datapulse.etl.config.IngestProperties;
import io.datapulse.etl.persistence.JobExecutionRepository;
import io.datapulse.etl.persistence.JobExecutionRow;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * CAS acquisition of a {@code job_execution} row for worker processing.
 */
@Service
@RequiredArgsConstructor
public class IngestJobAcquisitionService {

  private final JobExecutionRepository jobExecutionRepository;
  private final IngestProperties ingestProperties;
  private final Clock clock;

  /**
   * @param rabbitMqRedelivered {@code true} when RabbitMQ sets the redelivered flag (previous
   *     consumer did not ack — e.g. JVM killed mid-flight). Allows resuming an {@code IN_PROGRESS}
   *     job; without this, a redelivered message would fail CAS and be acked with no progress
   *     ({@code AcknowledgeMode.AUTO}). Additionally, {@link
   *     IngestProperties#inProgressOrphanReclaimThreshold()} allows reclaim when {@code
   *     redelivered=false} but {@code started_at} is old enough (broker quirks).
   * @return {@code true} if this worker may run the DAG (fresh acquire or reclaim).
   */
  public boolean tryAcquire(JobExecutionRow job, boolean rabbitMqRedelivered) {
    JobExecutionStatus currentStatus = JobExecutionStatus.valueOf(job.getStatus());

    return switch (currentStatus) {
      case PENDING -> jobExecutionRepository.casStatus(
          job.getId(), JobExecutionStatus.PENDING, JobExecutionStatus.IN_PROGRESS);
      case RETRY_SCHEDULED -> jobExecutionRepository.casStatus(
          job.getId(), JobExecutionStatus.RETRY_SCHEDULED, JobExecutionStatus.IN_PROGRESS);
      case IN_PROGRESS -> {
        if (!shouldReclaimInProgress(job, rabbitMqRedelivered)) {
          yield false;
        }
        yield jobExecutionRepository.reclaimInProgressAfterBrokerRedelivery(job.getId());
      }
      default -> false;
    };
  }

  private boolean shouldReclaimInProgress(JobExecutionRow job, boolean rabbitMqRedelivered) {
    if (rabbitMqRedelivered) {
      return true;
    }
    Duration threshold = ingestProperties.inProgressOrphanReclaimThreshold();
    if (threshold == null || threshold.isZero()) {
      return false;
    }
    OffsetDateTime started = job.getStartedAt();
    if (started == null) {
      return false;
    }
    return started.toInstant().isBefore(clock.instant().minus(threshold));
  }
}
