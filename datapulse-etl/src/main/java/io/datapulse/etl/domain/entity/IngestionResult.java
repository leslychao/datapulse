package io.datapulse.etl.domain.entity;

import java.util.UUID;

public record IngestionResult(UUID eventId, int receivedItems, int acceptedItems, int rejectedItems) {

  public IngestionResult {
    if (eventId == null) {
      throw new IllegalArgumentException("Event id is required for ingestion result");
    }
    if (receivedItems < 0 || acceptedItems < 0 || rejectedItems < 0) {
      throw new IllegalArgumentException("Ingestion counters cannot be negative");
    }
  }
}
