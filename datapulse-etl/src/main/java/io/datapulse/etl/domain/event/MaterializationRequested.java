package io.datapulse.etl.domain.event;

import io.datapulse.etl.domain.entity.MaterializationPlan;
import java.time.Instant;
import java.util.UUID;

public record MaterializationRequested(UUID eventId, MaterializationPlan plan, Instant occurredAt)
    implements DomainEvent {

  public MaterializationRequested {
    if (eventId == null) {
      throw new IllegalArgumentException("Event id is required");
    }
    if (plan == null) {
      throw new IllegalArgumentException("Materialization plan is required");
    }
    if (occurredAt == null) {
      throw new IllegalArgumentException("Event time is required");
    }
  }
}
