package io.datapulse.etl.scheduling;

import java.util.Map;

import io.datapulse.etl.domain.ConnectionStaleJobReconciler;
import io.datapulse.etl.domain.IngestResultReporter;
import io.datapulse.etl.persistence.JobExecutionRepository;
import io.datapulse.platform.outbox.OutboxEventType;
import io.datapulse.platform.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a {@code job_execution} + outbox event for a single connection, guarded by stale-job
 * reconciliation and active-job check. Extracted from {@link SyncScheduler} so that
 * {@link Transactional} is applied via Spring AOP proxy (not via self-invocation).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SyncDispatcher {

  private final JobExecutionRepository jobExecutionRepository;
  private final ConnectionStaleJobReconciler connectionStaleJobReconciler;
  private final OutboxService outboxService;
  private final IngestResultReporter resultReporter;

  @Transactional
  public void dispatchIfNotActive(long connectionId) {
    connectionStaleJobReconciler.reconcileForDispatch(connectionId);
    if (jobExecutionRepository.existsActiveForConnection(connectionId)) {
      log.debug("Active job already exists for connection: connectionId={}", connectionId);
      return;
    }

    long jobId = jobExecutionRepository.insert(connectionId, "INCREMENTAL");

    outboxService.createEvent(
        OutboxEventType.ETL_SYNC_EXECUTE,
        "job_execution",
        jobId,
        Map.of("jobExecutionId", jobId, "connectionId", connectionId));

    resultReporter.updateSyncStateSyncing(connectionId);

    log.info("Sync dispatched: connectionId={}, jobExecutionId={}", connectionId, jobId);
  }
}
