package io.datapulse.etl.domain.event;

import io.datapulse.etl.domain.entity.ExecutionStatus;
import java.time.Instant;
import java.util.UUID;

public record ExecutionStatusChanged(UUID executionId, ExecutionStatus from, ExecutionStatus to,
                                     Instant occurredAt) implements DomainEvent {

  public ExecutionStatusChanged {
    if (executionId == null) {
      throw new IllegalArgumentException("Execution id is required");
    }
    if (from == null || to == null) {
      throw new IllegalArgumentException("Both statuses are required");
    }
    if (occurredAt == null) {
      throw new IllegalArgumentException("Timestamp is required");
    }
  }
}
