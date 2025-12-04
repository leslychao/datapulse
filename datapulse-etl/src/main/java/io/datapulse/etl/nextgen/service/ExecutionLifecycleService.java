package io.datapulse.etl.nextgen.service;

import io.datapulse.etl.nextgen.dto.ExecutionResult;
import io.datapulse.etl.nextgen.dto.ExecutionStatus;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class ExecutionLifecycleService {

  private final Map<UUID, ExecutionStatus> executions = new ConcurrentHashMap<>();
  private final Map<UUID, OffsetDateTime> startedAt = new ConcurrentHashMap<>();
  private final Map<UUID, Integer> retryCounters = new ConcurrentHashMap<>();

  public void register(UUID executionId) {
    executions.put(executionId, ExecutionStatus.PENDING);
    startedAt.put(executionId, OffsetDateTime.now());
    retryCounters.putIfAbsent(executionId, 0);
  }

  public void markInProgress(UUID executionId) {
    executions.put(executionId, ExecutionStatus.IN_PROGRESS);
  }

  public void markWaitingRetry(UUID executionId, int retryCount) {
    retryCounters.put(executionId, retryCount);
    executions.put(executionId, ExecutionStatus.WAITING_RETRY);
  }

  public void markSuccess(UUID executionId) {
    executions.put(executionId, ExecutionStatus.SUCCESS);
  }

  public void markNoData(UUID executionId) {
    executions.put(executionId, ExecutionStatus.NO_DATA);
  }

  public void markFailed(UUID executionId) {
    executions.put(executionId, ExecutionStatus.FAILED_FINAL);
  }

  public ExecutionResult buildResult(
      UUID executionId,
      String eventId,
      long rawRows,
      String errorCode,
      String errorMessage
  ) {
    ExecutionStatus status = Optional.ofNullable(executions.get(executionId))
        .orElse(ExecutionStatus.PENDING);
    return new ExecutionResult(executionId, eventId, status, rawRows, errorCode, errorMessage);
  }

  public ExecutionStatus status(UUID executionId) {
    return Optional.ofNullable(executions.get(executionId)).orElse(ExecutionStatus.PENDING);
  }

  public int retryCount(UUID executionId) {
    return retryCounters.getOrDefault(executionId, 0);
  }

  public OffsetDateTime startedAt(UUID executionId) {
    return startedAt.get(executionId);
  }
}
