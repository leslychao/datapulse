package io.datapulse.etl.integration.messaging;

import io.datapulse.etl.domain.entity.MaterializationPlan;
import java.time.Instant;
import java.util.UUID;

public record MaterializationCommand(UUID eventId, MaterializationPlan plan, Instant timestamp) {

  public MaterializationCommand {
    if (eventId == null) {
      throw new IllegalArgumentException("Event id is required");
    }
    if (plan == null) {
      throw new IllegalArgumentException("Materialization plan is required");
    }
    if (timestamp == null) {
      throw new IllegalArgumentException("Timestamp is required");
    }
  }
}
