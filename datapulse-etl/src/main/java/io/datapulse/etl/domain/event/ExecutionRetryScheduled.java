package io.datapulse.etl.domain.event;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public record ExecutionRetryScheduled(UUID executionId, int attempt, Duration delay,
                                      Instant occurredAt) implements DomainEvent {

  public ExecutionRetryScheduled {
    if (executionId == null) {
      throw new IllegalArgumentException("Execution id is required");
    }
    if (attempt < 1) {
      throw new IllegalArgumentException("Attempt must be positive");
    }
    if (delay == null || delay.isNegative() || delay.isZero()) {
      throw new IllegalArgumentException("Delay must be positive");
    }
    if (occurredAt == null) {
      throw new IllegalArgumentException("Timestamp is required");
    }
  }
}
