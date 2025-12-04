package io.datapulse.etl.domain.event;

import io.datapulse.etl.domain.entity.ExecutionStatus;
import java.time.Instant;
import java.util.UUID;

public record ExecutionCompleted(UUID executionId, ExecutionStatus status, Instant occurredAt)
    implements DomainEvent {

  public ExecutionCompleted {
    if (executionId == null) {
      throw new IllegalArgumentException("Execution id is required");
    }
    if (status == null) {
      throw new IllegalArgumentException("Status is required");
    }
    if (occurredAt == null) {
      throw new IllegalArgumentException("Timestamp is required");
    }
  }
}
