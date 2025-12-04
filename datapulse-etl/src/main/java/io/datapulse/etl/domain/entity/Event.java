package io.datapulse.etl.domain.entity;

import java.time.Instant;
import java.util.UUID;

public final class Event {

  private final UUID id;
  private final String source;
  private final String payloadReference;
  private final EventStatus status;
  private final Instant createdAt;
  private final Instant updatedAt;

  public Event(UUID id, String source, String payloadReference, EventStatus status, Instant createdAt,
      Instant updatedAt) {
    if (id == null) {
      throw new IllegalArgumentException("Event id is required");
    }
    if (source == null || source.isBlank()) {
      throw new IllegalArgumentException("Event source is required");
    }
    if (payloadReference == null || payloadReference.isBlank()) {
      throw new IllegalArgumentException("Event payload reference is required");
    }
    if (status == null) {
      throw new IllegalArgumentException("Event status is required");
    }
    if (createdAt == null) {
      throw new IllegalArgumentException("Event creation time is required");
    }
    if (updatedAt == null) {
      throw new IllegalArgumentException("Event update time is required");
    }
    this.id = id;
    this.source = source;
    this.payloadReference = payloadReference;
    this.status = status;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public static Event received(UUID id, String source, String payloadReference, Instant timestamp) {
    return new Event(id, source, payloadReference, EventStatus.RECEIVED, timestamp, timestamp);
  }

  public UUID id() {
    return id;
  }

  public String source() {
    return source;
  }

  public String payloadReference() {
    return payloadReference;
  }

  public EventStatus status() {
    return status;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }

  public Event startProcessing(Instant timestamp) {
    return transition(EventStatus.IN_PROGRESS, timestamp);
  }

  public Event markMaterializationPending(Instant timestamp) {
    return transition(EventStatus.MATERIALIZATION_PENDING, timestamp);
  }

  public Event complete(Instant timestamp) {
    return transition(EventStatus.COMPLETED, timestamp);
  }

  public Event fail(Instant timestamp) {
    return transition(EventStatus.FAILED, timestamp);
  }

  public Event cancel(Instant timestamp) {
    return transition(EventStatus.CANCELLED, timestamp);
  }

  private Event transition(EventStatus next, Instant timestamp) {
    if (next == null) {
      throw new IllegalArgumentException("Next status is required");
    }
    if (timestamp == null) {
      throw new IllegalArgumentException("Transition time is required");
    }
    if (status == EventStatus.COMPLETED || status == EventStatus.CANCELLED) {
      throw new IllegalStateException("Event is already finalized");
    }
    return new Event(id, source, payloadReference, next, createdAt, timestamp);
  }
}
