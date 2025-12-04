package io.datapulse.etl.domain.event;

import java.time.Instant;
import java.util.UUID;

public record EventCreated(UUID eventId, Instant occurredAt) implements DomainEvent {

  public EventCreated {
    if (eventId == null) {
      throw new IllegalArgumentException("Event id is required");
    }
    if (occurredAt == null) {
      throw new IllegalArgumentException("Event creation time is required");
    }
  }
}
