package io.datapulse.etl.domain;

import com.fasterxml.jackson.databind.JsonNode;
import io.datapulse.etl.persistence.JobExecutionRepository;
import io.datapulse.etl.persistence.JobExecutionRow;
import io.datapulse.platform.etl.PostIngestMaterializationHook;
import io.datapulse.platform.etl.PostIngestMaterializationResult;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Handles {@link io.datapulse.platform.outbox.OutboxEventType#ETL_POST_INGEST_MATERIALIZE} after it
 * is delivered to the {@code etl.sync} consumer.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostIngestMaterializationMessageHandler {

  private final JobExecutionRepository jobExecutionRepository;
  private final PostIngestMaterializationHook postIngestMaterialization;
  private final IngestResultReporter resultReporter;
  private final StaleCampaignDetector staleCampaignDetector;
  private final TransactionTemplate transactionTemplate;

  public void handle(JsonNode payloadRoot) {
    PostIngestMaterializePayload payload = PostIngestMaterializePayload.fromJson(payloadRoot);
    if (payload.jobExecutionId() <= 0) {
      log.error("ETL_POST_INGEST_MATERIALIZE missing jobExecutionId: payload={}", payloadRoot);
      return;
    }

    JobExecutionRow job = jobExecutionRepository.findById(payload.jobExecutionId()).orElse(null);
    if (job == null) {
      log.warn("ETL_POST_INGEST_MATERIALIZE job not found: jobExecutionId={}",
          payload.jobExecutionId());
      return;
    }

    if (!JobExecutionStatus.MATERIALIZING.name().equals(job.getStatus())) {
      log.debug(
          "Skip ETL_POST_INGEST_MATERIALIZE: job not in MATERIALIZING: jobExecutionId={}, status={}",
          payload.jobExecutionId(),
          job.getStatus());
      return;
    }

    if (payload.ingestStatus() == null) {
      log.error("ETL_POST_INGEST_MATERIALIZE missing ingestStatus: jobExecutionId={}",
          payload.jobExecutionId());
      return;
    }
    JobExecutionStatus ingestStatus;
    try {
      ingestStatus = JobExecutionStatus.valueOf(payload.ingestStatus());
    } catch (Exception e) {
      log.error("ETL_POST_INGEST_MATERIALIZE invalid ingestStatus: jobExecutionId={}, raw={}",
          payload.jobExecutionId(), payload.ingestStatus(), e);
      return;
    }

    String ingestErrorDetails =
        job.getErrorDetails() != null ? job.getErrorDetails() : "{}";

    PostIngestMaterializationResult matResult;
    try {
      matResult = postIngestMaterialization.afterSuccessfulIngest(payload.jobExecutionId());
    } catch (Exception e) {
      log.error("Mart materialization failed after ingest: jobExecutionId={}",
          payload.jobExecutionId(), e);
      matResult = PostIngestMaterializationResult.fatal(
          e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
    }

    JobExecutionStatus terminalStatus = resolveTerminalStatus(ingestStatus, matResult);
    String finalErrorDetails =
        resultReporter.mergeMaterializationIntoErrorDetails(ingestErrorDetails, matResult);

    AtomicBoolean finalized = new AtomicBoolean(false);
    transactionTemplate.executeWithoutResult(tx -> {
      boolean advanced = jobExecutionRepository.casStatus(
          payload.jobExecutionId(),
          JobExecutionStatus.MATERIALIZING,
          terminalStatus);
      if (!advanced) {
        log.debug(
            "Skip ETL_POST_INGEST_MATERIALIZE finalize: CAS failed (jobExecutionId={}, target={})",
            payload.jobExecutionId(),
            terminalStatus);
        return;
      }
      finalized.set(true);
      jobExecutionRepository.updateErrorDetails(payload.jobExecutionId(), finalErrorDetails);
      if (terminalStatus == JobExecutionStatus.COMPLETED
          || terminalStatus == JobExecutionStatus.COMPLETED_WITH_ERRORS) {
        resultReporter.recordSuccessfulTerminalSyncLists(
            job,
            payload.workspaceId(),
            payload.completedDomains(),
            payload.failedDomains());
        if (payload.promoSyncCompleted()) {
          staleCampaignDetector.detectAndPublish(job.getConnectionId());
        }
      }
    });

    if (finalized.get()) {
      log.info("Post-ingest materialization finished: jobExecutionId={}, status={}",
          payload.jobExecutionId(), terminalStatus);
    }
  }

  private static JobExecutionStatus resolveTerminalStatus(
      JobExecutionStatus ingestStatus, PostIngestMaterializationResult matResult) {
    if (matResult.fullySucceeded()) {
      return ingestStatus;
    }
    return JobExecutionStatus.COMPLETED_WITH_ERRORS;
  }
}
