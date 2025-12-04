package io.datapulse.etl.integration.messaging;

import io.datapulse.etl.domain.entity.MaterializationPlan;
import java.time.Instant;
import java.util.UUID;

public record ExecutionCommand(UUID executionId, MaterializationPlan plan, Instant timestamp) {

  public ExecutionCommand {
    if (executionId == null) {
      throw new IllegalArgumentException("Execution id is required");
    }
    if (plan == null) {
      throw new IllegalArgumentException("Materialization plan is required");
    }
    if (timestamp == null) {
      throw new IllegalArgumentException("Timestamp is required");
    }
  }
}
