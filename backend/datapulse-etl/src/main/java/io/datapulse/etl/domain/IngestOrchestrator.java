package io.datapulse.etl.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import io.datapulse.etl.config.IngestProperties;
import io.datapulse.etl.persistence.JobExecutionRepository;
import io.datapulse.etl.persistence.JobExecutionRow;
import io.datapulse.integration.domain.MarketplaceType;
import io.datapulse.platform.etl.PostIngestMaterializationHook;
import io.datapulse.platform.outbox.OutboxEventType;
import io.datapulse.platform.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Entry point for ETL ingest pipeline.
 *
 * <p>Called from RabbitMQ consumer when an {@code ETL_SYNC_EXECUTE} message arrives.
 * Orchestrates the full lifecycle:
 * <ol>
 *   <li>CAS job_execution PENDING → IN_PROGRESS (or RETRY_SCHEDULED → IN_PROGRESS)</li>
 *   <li>Resolve credentials from Vault</li>
 *   <li>Parse checkpoint (if DLX retry)</li>
 *   <li>Execute DAG (level-based parallelism)</li>
 *   <li>Determine final status based on event results</li>
 *   <li>Publish outbox events (ETL_SYNC_COMPLETED or ETL_SYNC_RETRY)</li>
 *   <li>Refresh ClickHouse mart tables (incremental materialization for this job)</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IngestOrchestrator {

    private final JobExecutionRepository jobExecutionRepository;
    private final CredentialResolver credentialResolver;
    private final CheckpointManager checkpointManager;
    private final DagExecutor dagExecutor;
    private final OutboxService outboxService;
    private final IngestResultReporter resultReporter;
    private final StaleCampaignDetector staleCampaignDetector;
    private final IngestProperties ingestProperties;
    private final TransactionTemplate transactionTemplate;
    private final PostIngestMaterializationHook postIngestMaterialization;

    /**
     * Main entry point — processes a single sync job.
     *
     * @param jobExecutionId PK of job_execution record
     */
    public void processSync(long jobExecutionId) {
        log.info("Ingest started: jobExecutionId={}", jobExecutionId);

        JobExecutionRow job = jobExecutionRepository.findById(jobExecutionId)
                .orElseThrow(() -> new IllegalStateException(
                        "job_execution not found: id=%d".formatted(jobExecutionId)));

        if (!acquireJob(job)) {
            log.warn("Could not acquire job (CAS failed): jobExecutionId={}, currentStatus={}",
                    jobExecutionId, job.getStatus());
            return;
        }

        try {
            IngestContext context = buildContext(job);

            Map<EtlEventType, EventResult> results = dagExecutor.execute(context);

            completeJob(job, context, results);
        } catch (Exception e) {
            log.error("Ingest failed with unexpected error: jobExecutionId={}", jobExecutionId, e);
            jobExecutionRepository.casStatus(
                    jobExecutionId, JobExecutionStatus.IN_PROGRESS, JobExecutionStatus.FAILED);
            jobExecutionRepository.updateErrorDetails(jobExecutionId,
                    "{\"error\": \"%s\"}".formatted(escapeJson(e.getMessage())));
        }
    }

    private boolean acquireJob(JobExecutionRow job) {
        JobExecutionStatus currentStatus = JobExecutionStatus.valueOf(job.getStatus());

        return switch (currentStatus) {
            case PENDING -> jobExecutionRepository.casStatus(
                    job.getId(), JobExecutionStatus.PENDING, JobExecutionStatus.IN_PROGRESS);
            case RETRY_SCHEDULED -> jobExecutionRepository.casStatus(
                    job.getId(), JobExecutionStatus.RETRY_SCHEDULED, JobExecutionStatus.IN_PROGRESS);
            default -> false;
        };
    }

    private IngestContext buildContext(JobExecutionRow job) {
        CredentialResolver.ResolvedCredentials creds = credentialResolver.resolve(job.getConnectionId());

        Map<EtlEventType, IngestContext.CheckpointEntry> checkpoint =
                checkpointManager.parse(job.getCheckpoint());

        Set<EtlEventType> scope = resolveScope(job.getEventType(), creds.marketplace());

        return new IngestContext(
                job.getId(),
                job.getConnectionId(),
                creds.workspaceId(),
                creds.marketplace(),
                creds.credentials(),
                job.getEventType(),
                scope,
                checkpoint
        );
    }

    private Set<EtlEventType> resolveScope(String eventType, MarketplaceType marketplace) {
        if ("FULL_SYNC".equals(eventType)) {
            return DagDefinition.fullSyncScope();
        }

        // INCREMENTAL: all events in DAG (filtering by data domain happens at adapter level)
        return EnumSet.allOf(EtlEventType.class);
    }

    private void completeJob(JobExecutionRow job, IngestContext context,
                             Map<EtlEventType, EventResult> results) {
        long jobId = job.getId();
        JobExecutionStatus finalStatus = determineFinalStatus(results);

        boolean hasRetriableFailures = hasRetriableFailures(results);
        int retryCount = checkpointManager.extractRetryCount(job.getCheckpoint());

        if (hasRetriableFailures && retryCount < ingestProperties.maxJobRetries()) {
            scheduleRetry(job, context, results, retryCount);
            return;
        }

        transactionTemplate.executeWithoutResult(tx -> {
            jobExecutionRepository.casStatus(jobId, JobExecutionStatus.IN_PROGRESS, finalStatus);
            jobExecutionRepository.updateErrorDetails(jobId, resultReporter.buildErrorDetails(results));
            jobExecutionRepository.updateCheckpoint(jobId,
                    checkpointManager.serialize(results, retryCount));

            if (finalStatus == JobExecutionStatus.COMPLETED
                    || finalStatus == JobExecutionStatus.COMPLETED_WITH_ERRORS) {
                resultReporter.updateSyncStateSuccess(job.getConnectionId());
                resultReporter.publishCompletionEvent(job, results);

                if (isPromoSyncCompleted(results)) {
                    staleCampaignDetector.detectAndPublish(job.getConnectionId());
                }
            }
        });

        if (finalStatus == JobExecutionStatus.COMPLETED
                || finalStatus == JobExecutionStatus.COMPLETED_WITH_ERRORS) {
            try {
                postIngestMaterialization.afterSuccessfulIngest(jobId);
            } catch (Exception e) {
                log.error("Mart materialization failed after ingest: jobExecutionId={}", jobId, e);
            }
        }

        log.info("Ingest completed: jobExecutionId={}, status={}", jobId, finalStatus);
    }

    private void scheduleRetry(JobExecutionRow job, IngestContext context,
                                Map<EtlEventType, EventResult> results, int retryCount) {
        int nextRetry = retryCount + 1;
        long delayMs = calculateRetryDelay(retryCount);

        transactionTemplate.executeWithoutResult(tx -> {
            jobExecutionRepository.casStatus(
                    job.getId(), JobExecutionStatus.IN_PROGRESS, JobExecutionStatus.RETRY_SCHEDULED);
            jobExecutionRepository.updateCheckpoint(job.getId(),
                    checkpointManager.serialize(results, nextRetry));
            jobExecutionRepository.updateErrorDetails(job.getId(), resultReporter.buildErrorDetails(results));

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

    private String escapeJson(String value) {
        if (value == null) {
            return "null";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
