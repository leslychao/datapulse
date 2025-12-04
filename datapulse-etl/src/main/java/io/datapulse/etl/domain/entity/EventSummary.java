package io.datapulse.etl.domain.entity;

import java.util.UUID;

public final class EventSummary {

  private final UUID eventId;
  private final int ingestedItems;
  private final int normalizedItems;
  private final int failedItems;

  public EventSummary(UUID eventId, int ingestedItems, int normalizedItems, int failedItems) {
    if (eventId == null) {
      throw new IllegalArgumentException("Event id is required for summary");
    }
    if (ingestedItems < 0 || normalizedItems < 0 || failedItems < 0) {
      throw new IllegalArgumentException("Summary counters cannot be negative");
    }
    this.eventId = eventId;
    this.ingestedItems = ingestedItems;
    this.normalizedItems = normalizedItems;
    this.failedItems = failedItems;
  }

  public UUID eventId() {
    return eventId;
  }

  public int ingestedItems() {
    return ingestedItems;
  }

  public int normalizedItems() {
    return normalizedItems;
  }

  public int failedItems() {
    return failedItems;
  }
}
