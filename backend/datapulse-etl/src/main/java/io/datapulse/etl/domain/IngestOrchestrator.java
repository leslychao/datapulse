package io.datapulse.etl.domain;

import io.datapulse.etl.persistence.JobExecutionRepository;
import io.datapulse.etl.persistence.JobExecutionRow;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Entry point for ETL ingest pipeline.
 *
 * <p>Delegates acquisition, context build, DAG execution, and completion to focused services;
 * see {@link IngestJobCompletionCoordinator} for terminal status, materialization, and
 * {@code ETL_SYNC_COMPLETED} invariants (documented in {@code docs/modules/etl-pipeline.md}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IngestOrchestrator {

  private final JobExecutionRepository jobExecutionRepository;
  private final DagExecutor dagExecutor;
  private final IngestResultReporter resultReporter;
  private final IngestJobAcquisitionService jobAcquisition;
  private final IngestSyncContextBuilder syncContextBuilder;
  private final IngestJobCompletionCoordinator jobCompletion;

  /**
   * Same as {@link #processSync(long, boolean)} with {@code rabbitMqRedelivered=false} (tests,
   * non-Rabbit callers).
   */
  public void processSync(long jobExecutionId) {
    processSync(jobExecutionId, false);
  }

  /**
   * Main entry point — processes a single sync job.
   *
   * @param jobExecutionId PK of job_execution record
   * @param rabbitMqRedelivered pass {@code MessageProperties#getRedelivered()} from the AMQP message
   */
  public void processSync(long jobExecutionId, boolean rabbitMqRedelivered) {
    log.info("Ingest started: jobExecutionId={}", jobExecutionId);

    JobExecutionRow job = jobExecutionRepository.findById(jobExecutionId)
        .orElseThrow(() -> new IllegalStateException(
            "job_execution not found: id=%d".formatted(jobExecutionId)));

    if (!jobAcquisition.tryAcquire(job, rabbitMqRedelivered)) {
      log.warn(
          "Could not acquire job (CAS failed): jobExecutionId={}, currentStatus={}, redelivered={}",
          jobExecutionId, job.getStatus(), rabbitMqRedelivered);
      return;
    }

    try {
      IngestContext context = syncContextBuilder.build(job);
      Map<EtlEventType, EventResult> results = dagExecutor.execute(context);
      jobCompletion.completeAfterDag(job, context, results);
    } catch (Exception e) {
      log.error("Ingest failed with unexpected error: jobExecutionId={}", jobExecutionId, e);
      jobExecutionRepository.casStatus(
          jobExecutionId, JobExecutionStatus.IN_PROGRESS, JobExecutionStatus.FAILED);
      jobExecutionRepository.updateErrorDetails(jobExecutionId,
          "{\"error\": \"%s\"}".formatted(escapeJson(e.getMessage())));
      resultReporter.updateSyncStateError(job.getConnectionId(), e.getMessage());
    }
  }

  private static String escapeJson(String value) {
    if (value == null) {
      return "null";
    }
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
