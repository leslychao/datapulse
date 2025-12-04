package io.datapulse.etl.domain.entity;

import java.time.Duration;

public record BackoffRequest(int attempt, Duration baseDelay) {

  public BackoffRequest {
    if (attempt < 1) {
      throw new IllegalArgumentException("Attempt must be positive");
    }
    if (baseDelay == null || baseDelay.isNegative() || baseDelay.isZero()) {
      throw new IllegalArgumentException("Base delay must be positive");
    }
  }
}
