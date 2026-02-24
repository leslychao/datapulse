package io.datapulse.etl.v1.execution;

import io.datapulse.etl.event.EtlSourceRegistry;
import io.datapulse.etl.event.EtlSourceRegistry.RegisteredSource;
import io.datapulse.etl.repository.jdbc.RawBatchInsertJdbcRepository;
import io.datapulse.etl.v1.dto.EtlIngestExecutionContext;
import io.datapulse.etl.v1.dto.EtlSourceExecution;
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
  private final EtlExecutionOutboxRepository outboxRepository;
  private final EtlExecutionPayloadCodec payloadCodec;

  /**
   * TX-bound подготовка execution к ingest:
   * - проверка, что execution/source не terminal
   * - mark IN_PROGRESS (CAS)
   * - delete raw by requestId (как у тебя сейчас)
   * - построение EtlIngestExecutionContext (execution + registeredSource)
   *
   * Возвращает null, если делать нечего (terminal/не удалось CAS).
   */
  @Transactional
  public EtlIngestExecutionContext prepareIngest(EtlSourceExecution execution) {
    var executionRow = stateRepository.findExecution(execution.requestId()).orElse(null);
    if (executionRow == null || executionRow.isTerminal()) {
      return null;
    }

    var sourceState = stateRepository
        .findSourceState(execution.requestId(), execution.event(), execution.sourceId())
        .orElse(null);
    if (sourceState == null || sourceState.status().isTerminal()) {
      return null;
    }

    if (!stateRepository.markSourceInProgress(execution.requestId(), execution.event(), execution.sourceId())) {
      return null;
    }

    RegisteredSource source = sourceRegistry.getSources(execution.event()).stream()
        .filter(item -> item.sourceId().equals(execution.sourceId()))
        .findFirst()
        .orElseThrow();

    // твоя текущая семантика: delete-before-insert
    rawBatchRepository.deleteByRequestId(source.rawTable(), execution.requestId());

    return new EtlIngestExecutionContext(execution, source);
  }

  /**
   * Финализация ПОСЛЕ завершения ingest.
   */
  @Transactional
  public void finalizeIngest(EtlIngestExecutionContext ctx) {
    EtlSourceExecution execution = ctx.execution();
    stateRepository.markSourceCompleted(execution.requestId(), execution.event(), execution.sourceId());
    stateRepository.resolveExecutionStatus(execution.requestId());
  }

  /**
   * Унифицированный обработчик ошибок ingest в TX-контексте.
   * Его можно дергать из error-flow / advice (см. комментарий в конфиге).
   */
  @Transactional
  public void handleIngestFailure(EtlIngestExecutionContext ctx, Throwable error) {
    EtlSourceExecution execution = ctx.execution();

    if (error instanceof TooManyRequestsBackoffRequiredException remote) {
      scheduleRetry(execution, "REMOTE_429",
          Math.max(0, remote.getRetryAfterSeconds()) * 1000L,
          error.getMessage());
      return;
    }
    if (error instanceof LocalRateLimitBackoffRequiredException local) {
      scheduleRetry(execution, "LOCAL_RATE_LIMIT",
          local.getRetryAfterMillis(),
          error.getMessage());
      return;
    }

    stateRepository.markSourceFailedTerminal(
        execution.requestId(),
        execution.event(),
        execution.sourceId(),
        error.getClass().getSimpleName(),
        error.getMessage()
    );
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
