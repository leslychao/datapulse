package io.datapulse.etl.integration.messaging;

import java.time.Instant;
import java.util.UUID;

public record OrchestrationCommand(UUID eventId, String source, String payloadReference, Instant timestamp) {

  public OrchestrationCommand {
    if (eventId == null) {
      throw new IllegalArgumentException("eventId is required");
    }
    if (source == null || source.isBlank()) {
      throw new IllegalArgumentException("source is required");
    }
    if (payloadReference == null || payloadReference.isBlank()) {
      throw new IllegalArgumentException("payloadReference is required");
    }
    if (timestamp == null) {
      throw new IllegalArgumentException("timestamp is required");
    }
  }
}
