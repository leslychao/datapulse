package io.datapulse.etl.domain.event;

import java.time.Instant;
import java.util.UUID;

public record MaterializationSkipped(UUID eventId, String reason, Instant occurredAt)
    implements DomainEvent {

  public MaterializationSkipped {
    if (eventId == null) {
      throw new IllegalArgumentException("Event id is required");
    }
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("Skip reason is required");
    }
    if (occurredAt == null) {
      throw new IllegalArgumentException("Timestamp is required");
    }
  }
}
