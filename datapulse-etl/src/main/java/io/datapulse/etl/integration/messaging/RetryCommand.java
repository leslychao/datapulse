package io.datapulse.etl.integration.messaging;

import io.datapulse.etl.domain.entity.BackoffRequest;
import java.time.Instant;
import java.util.UUID;

public record RetryCommand(UUID executionId, BackoffRequest request, Instant timestamp) {

  public RetryCommand {
    if (executionId == null) {
      throw new IllegalArgumentException("Execution id is required");
    }
    if (request == null) {
      throw new IllegalArgumentException("Backoff request is required");
    }
    if (timestamp == null) {
      throw new IllegalArgumentException("Timestamp is required");
    }
  }
}
