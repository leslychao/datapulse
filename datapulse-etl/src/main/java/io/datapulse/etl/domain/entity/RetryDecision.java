package io.datapulse.etl.domain.entity;

import java.time.Duration;

public record RetryDecision(boolean shouldRetry, Duration retryDelay, ExecutionStatus terminalStatus) {

  public RetryDecision {
    if (shouldRetry && (retryDelay == null || retryDelay.isNegative() || retryDelay.isZero())) {
      throw new IllegalArgumentException("Retry delay must be positive when retrying");
    }
    if (!shouldRetry && terminalStatus == null) {
      throw new IllegalArgumentException("Terminal status is required when no retry");
    }
  }
}
