package io.datapulse.etl.domain.event;

import io.datapulse.etl.domain.entity.EventStatus;
import java.time.Instant;
import java.util.UUID;

public record EventCompleted(UUID eventId, EventStatus finalStatus, Instant occurredAt)
    implements DomainEvent {

  public EventCompleted {
    if (eventId == null) {
      throw new IllegalArgumentException("Event id is required");
    }
    if (finalStatus == null) {
      throw new IllegalArgumentException("Final status is required");
    }
    if (occurredAt == null) {
      throw new IllegalArgumentException("Completion time is required");
    }
  }
}
