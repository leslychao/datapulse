package io.datapulse.etl.integration.messaging;

import java.time.Instant;
import java.util.UUID;

public record EventStartCommand(UUID eventId, String source, String payloadReference, Instant timestamp) {

  public EventStartCommand {
    if (eventId == null) {
      throw new IllegalArgumentException("Event id is required");
    }
    if (source == null || source.isBlank()) {
      throw new IllegalArgumentException("Source is required");
    }
    if (payloadReference == null || payloadReference.isBlank()) {
      throw new IllegalArgumentException("Payload reference is required");
    }
    if (timestamp == null) {
      throw new IllegalArgumentException("Timestamp is required");
    }
  }
}
