package io.datapulse.etl.integration.web;

import java.time.Instant;
import java.util.UUID;

public record OrchestrationAcceptedResponse(UUID eventId, Instant acceptedAt) {

  public OrchestrationAcceptedResponse {
    if (eventId == null) {
      throw new IllegalArgumentException("eventId is required");
    }
    if (acceptedAt == null) {
      throw new IllegalArgumentException("acceptedAt is required");
    }
  }
}
