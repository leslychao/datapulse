package io.datapulse.etl.integration.web;

import java.util.UUID;

public record EtlRunRequest(UUID eventId, String source, String payloadReference) {

  public EtlRunRequest {
    if (eventId == null) {
      throw new IllegalArgumentException("eventId is required");
    }
    if (source == null || source.isBlank()) {
      throw new IllegalArgumentException("source is required");
    }
    if (payloadReference == null || payloadReference.isBlank()) {
      throw new IllegalArgumentException("payloadReference is required");
    }
  }
}
