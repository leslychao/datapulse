package io.datapulse.etl.domain;

import io.datapulse.etl.persistence.JobExecutionRepository;
import io.datapulse.etl.persistence.JobExecutionRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * CAS acquisition of a {@code job_execution} row for worker processing.
 */
@Service
@RequiredArgsConstructor
public class IngestJobAcquisitionService {

  private final JobExecutionRepository jobExecutionRepository;

  /**
   * @return {@code true} if this worker acquired the job (PENDING or RETRY_SCHEDULED → IN_PROGRESS).
   */
  public boolean tryAcquire(JobExecutionRow job) {
    JobExecutionStatus currentStatus = JobExecutionStatus.valueOf(job.getStatus());

    return switch (currentStatus) {
      case PENDING -> jobExecutionRepository.casStatus(
          job.getId(), JobExecutionStatus.PENDING, JobExecutionStatus.IN_PROGRESS);
      case RETRY_SCHEDULED -> jobExecutionRepository.casStatus(
          job.getId(), JobExecutionStatus.RETRY_SCHEDULED, JobExecutionStatus.IN_PROGRESS);
      default -> false;
    };
  }
}
