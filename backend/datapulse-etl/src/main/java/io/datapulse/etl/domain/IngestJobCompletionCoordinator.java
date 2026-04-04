package io.datapulse.etl.domain;

import io.datapulse.etl.config.IngestProperties;
import io.datapulse.etl.config.PostIngestMaterializationMode;
import io.datapulse.etl.persistence.JobExecutionRepository;
import io.datapulse.etl.persistence.JobExecutionRow;
import io.datapulse.platform.etl.PostIngestMaterializationHook;
import io.datapulse.platform.etl.PostIngestMaterializationResult;
import io.datapulse.platform.outbox.OutboxEventType;
import io.datapulse.platform.outbox.OutboxService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Terminal phase of ingest: retry scheduling, full failure, or materialization + success fan-out.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IngestJobCompletionCoordinator {

  private final JobExecutionRepository jobExecutionRepository;
  private final CheckpointManager checkpointManager;
  private final IngestResultReporter resultReporter;
  private final StaleCampaignDetector staleCampaignDetector;
  private final IngestProperties ingestProperties;
  private final TransactionTemplate transactionTemplate;
  private final PostIngestMaterializationHook postIngestMaterialization;
  private final OutboxService outboxService;

  public void completeAfterDag(
      JobExecutionRow job, IngestContext context, Map<EtlEventType, EventResult> results) {
    long jobId = job.getId();
    JobExecutionStatus ingestStatus = determineFinalStatus(results);

    boolean hasRetriableFailures = hasRetriableFailures(results);
    int retryCount = checkpointManager.extractRetryCount(job.getCheckpoint());

    if (hasRetriableFailures && retryCount < ingestProperties.maxJobRetries()) {
      scheduleRetry(job, results, retryCount);
      return;
    }

    if (ingestStatus == JobExecutionStatus.FAILED) {
      transactionTemplate.executeWithoutResult(tx -> {
        jobExecutionRepository.casStatus(jobId, JobExecutionStatus.IN_PROGRESS,
            JobExecutionStatus.FAILED);
        jobExecutionRepository.updateErrorDetails(jobId,
            resultReporter.buildErrorDetails(results));
        jobExecutionRepository.updateCheckpoint(jobId,
            checkpointManager.serialize(results, retryCount));
        resultReporter.updateSyncStateError(job.getConnectionId(),
            "Sync failed: all domains returned errors");
      });
      log.info("Ingest completed: jobExecutionId={}, status={}", jobId, ingestStatus);
      return;
    }

    String ingestErrorDetails = resultReporter.buildErrorDetails(results);
    String checkpointJson = checkpointManager.serialize(results, retryCount);

    if (ingestProperties.postIngestMaterializationMode() == PostIngestMaterializationMode.ASYNC_OUTBOX) {
      transactionTemplate.executeWithoutResult(tx -> {
        jobExecutionRepository.casStatus(jobId, JobExecutionStatus.IN_PROGRESS,
            JobExecutionStatus.MATERIALIZING);
        jobExecutionRepository.updateErrorDetails(jobId, ingestErrorDetails);
        jobExecutionRepository.updateCheckpoint(jobId, checkpointJson);
        PostIngestMaterializePayload materializePayload =
            PostIngestMaterializePayload.from(job, context.workspaceId(), ingestStatus, results);
        outboxService.createEvent(
            OutboxEventType.ETL_POST_INGEST_MATERIALIZE,
            "job_execution",
            jobId,
            materializePayload.toOutboxPayload());
      });
      log.info(
          "Post-ingest materialization scheduled via outbox: jobExecutionId={}, ingestStatus={}",
          jobId,
          ingestStatus);
      return;
    }

    transactionTemplate.executeWithoutResult(tx -> {
      jobExecutionRepository.casStatus(jobId, JobExecutionStatus.IN_PROGRESS,
          JobExecutionStatus.MATERIALIZING);
      jobExecutionRepository.updateErrorDetails(jobId, ingestErrorDetails);
      jobExecutionRepository.updateCheckpoint(jobId, checkpointJson);
    });

    PostIngestMaterializationResult matResult;
    try {
      matResult = postIngestMaterialization.afterSuccessfulIngest(jobId);
    } catch (Exception e) {
      log.error("Mart materialization failed after ingest: jobExecutionId={}", jobId, e);
      matResult = PostIngestMaterializationResult.fatal(
          e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
    }

    JobExecutionStatus terminalStatus = resolveTerminalStatus(ingestStatus, matResult);
    String finalErrorDetails =
        resultReporter.mergeMaterializationIntoErrorDetails(ingestErrorDetails, matResult);

    transactionTemplate.executeWithoutResult(tx -> {
      jobExecutionRepository.casStatus(jobId, JobExecutionStatus.MATERIALIZING, terminalStatus);
      jobExecutionRepository.updateErrorDetails(jobId, finalErrorDetails);
      if (terminalStatus == JobExecutionStatus.COMPLETED
          || terminalStatus == JobExecutionStatus.COMPLETED_WITH_ERRORS) {
        resultReporter.recordSuccessfulTerminalSync(job, context.workspaceId(), results);

        if (isPromoSyncCompleted(results)) {
          staleCampaignDetector.detectAndPublish(job.getConnectionId());
        }
      }
    });

    log.info("Ingest completed: jobExecutionId={}, status={}", jobId, terminalStatus);
  }

  private static JobExecutionStatus resolveTerminalStatus(
      JobExecutionStatus ingestStatus, PostIngestMaterializationResult matResult) {
    if (matResult.fullySucceeded()) {
      return ingestStatus;
    }
    return JobExecutionStatus.COMPLETED_WITH_ERRORS;
  }

  private void scheduleRetry(
      JobExecutionRow job, Map<EtlEventType, EventResult> results, int retryCount) {
    int nextRetry = retryCount + 1;
    long delayMs = calculateRetryDelay(retryCount);

    transactionTemplate.executeWithoutResult(tx -> {
      jobExecutionRepository.casStatus(
          job.getId(), JobExecutionStatus.IN_PROGRESS, JobExecutionStatus.RETRY_SCHEDULED);
      jobExecutionRepository.updateCheckpoint(job.getId(),
          checkpointManager.serialize(results, nextRetry));
      jobExecutionRepository.updateErrorDetails(job.getId(),
          resultReporter.buildErrorDetails(results));

      outboxService.createEvent(
          OutboxEventType.ETL_SYNC_RETRY,
          "job_execution",
          job.getId(),
          Map.of("jobExecutionId", job.getId(),
              "connectionId", job.getConnectionId(),
              "retryCount", nextRetry,
              "delay_ms", delayMs));
    });

    log.info("Retry scheduled: jobExecutionId={}, retryCount={}/{}, delayMs={}",
        job.getId(), nextRetry, ingestProperties.maxJobRetries(), delayMs);
  }

  private long calculateRetryDelay(int retryCount) {
    long delayMs = ingestProperties.minRetryBackoff().toMillis()
        * (long) Math.pow(ingestProperties.retryBackoffMultiplier(), retryCount);
    return Math.min(delayMs, ingestProperties.maxRetryBackoff().toMillis());
  }

  private JobExecutionStatus determineFinalStatus(Map<EtlEventType, EventResult> results) {
    boolean anyFailed = results.values().stream().anyMatch(EventResult::isFailed);
    boolean anyPartial = results.values().stream()
        .anyMatch(r -> r.status() == EventResultStatus.COMPLETED_WITH_ERRORS);
    boolean allFailed = results.values().stream()
        .allMatch(r -> r.isFailed() || r.isSkipped());

    if (allFailed) {
      return JobExecutionStatus.FAILED;
    }
    if (anyFailed || anyPartial) {
      return JobExecutionStatus.COMPLETED_WITH_ERRORS;
    }
    return JobExecutionStatus.COMPLETED;
  }

  private boolean hasRetriableFailures(Map<EtlEventType, EventResult> results) {
    return results.values().stream()
        .anyMatch(r -> r.isFailed() && !r.subSourceResults().isEmpty()
            && r.subSourceResults().stream()
            .anyMatch(s -> s.status() == EventResultStatus.FAILED));
  }

  private boolean isPromoSyncCompleted(Map<EtlEventType, EventResult> results) {
    EventResult promoResult = results.get(EtlEventType.PROMO_SYNC);
    return promoResult != null && promoResult.isSuccess();
  }
}
