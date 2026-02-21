package io.datapulse.etl.v1.execution;

import io.datapulse.etl.event.EtlSourceRegistry;
import io.datapulse.etl.event.EtlSourceRegistry.RegisteredSource;
import io.datapulse.etl.repository.jdbc.RawBatchInsertJdbcRepository;
import io.datapulse.etl.v1.dto.EtlSourceExecution;
import io.datapulse.etl.v1.flow.core.EtlIngestGateway;
import io.datapulse.etl.v1.flow.core.EtlSnapshotIngestionFlowConfig.IngestCommand;
import io.datapulse.marketplaces.resilience.TooManyRequestsBackoffRequiredException;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EtlExecutionWorkerTxService {

  private final EtlExecutionStateRepository stateRepository;
  private final EtlSourceRegistry sourceRegistry;
  private final RawBatchInsertJdbcRepository rawBatchRepository;
  private final EtlIngestGateway ingestGateway;
  private final EtlExecutionOutboxRepository outboxRepository;
  private final EtlExecutionPayloadCodec payloadCodec;

  @Transactional
  public void process(EtlSourceExecution execution) {
    var executionRow = stateRepository.findExecution(execution.requestId()).orElse(null);
    if (executionRow == null || executionRow.isTerminal()) {
      return;
    }

    var sourceState = stateRepository.findSourceState(execution.requestId(), execution.event(), execution.sourceId()).orElse(null);
    if (sourceState == null || sourceState.status().isTerminal()) {
      return;
    }

    if (!stateRepository.markSourceInProgress(execution.requestId(), execution.event(), execution.sourceId())) {
      return;
    }

    RegisteredSource source = sourceRegistry.getSources(execution.event()).stream()
        .filter(item -> item.sourceId().equals(execution.sourceId()))
        .findFirst()
        .orElseThrow();

    try {
      rawBatchRepository.deleteByRequestId(source.rawTable(), execution.requestId());
      ingestGateway.ingest(new IngestCommand(execution, source));
      stateRepository.markSourceCompleted(execution.requestId(), execution.event(), execution.sourceId());
      stateRepository.resolveExecutionStatus(execution.requestId());
    } catch (Throwable ex) {
      handleException(execution, ex);
    }
  }

  private void handleException(EtlSourceExecution execution, Throwable error) {
    if (error instanceof TooManyRequestsBackoffRequiredException remote) {
      scheduleRetry(execution, "REMOTE_429", Math.max(0, remote.getRetryAfterSeconds()) * 1000L, error.getMessage());
      return;
    }
    if (error instanceof LocalRateLimitBackoffRequiredException local) {
      scheduleRetry(execution, "LOCAL_RATE_LIMIT", local.getRetryAfterMillis(), error.getMessage());
      return;
    }
    stateRepository.markSourceFailedTerminal(
        execution.requestId(), execution.event(), execution.sourceId(), error.getClass().getSimpleName(), error.getMessage());
    stateRepository.resolveExecutionStatus(execution.requestId());
  }

  private void scheduleRetry(EtlSourceExecution execution, String code, long ttlMillis, String message) {
    OffsetDateTime nextAttemptAt = OffsetDateTime.now().plusNanos(ttlMillis * 1_000_000L);
    if (stateRepository.scheduleRetry(execution.requestId(), execution.event(), execution.sourceId(), code, message, nextAttemptAt)) {
      outboxRepository.enqueueWait(execution, payloadCodec.toJson(execution), ttlMillis);
    } else {
      stateRepository.markSourceFailedTerminal(execution.requestId(), execution.event(), execution.sourceId(), code, message);
      stateRepository.resolveExecutionStatus(execution.requestId());
    }
  }
}
