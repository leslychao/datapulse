package io.datapulse.etl.flow.core.model;

import java.util.Optional;

public record ExecutionOutcome(
    ExecutionDescriptor descriptor,
    ExecutionStatus status,
    String errorMessage,
    Integer retryAfterSeconds,
    String snapshotId
) {
  public boolean isTerminal() {
    return status == ExecutionStatus.SUCCESS
        || status == ExecutionStatus.NO_DATA
        || status == ExecutionStatus.ERROR;
  }

  public boolean isWaiting() {
    return status == ExecutionStatus.WAITING;
  }

  public Optional<Integer> retryAfterSecondsOptional() {
    return Optional.ofNullable(retryAfterSeconds);
  }
}
