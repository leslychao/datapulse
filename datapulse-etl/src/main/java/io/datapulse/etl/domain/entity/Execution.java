package io.datapulse.etl.domain.entity;

import java.time.Instant;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class Execution {

  private static final Map<ExecutionStatus, Set<ExecutionStatus>> TRANSITIONS =
      createTransitions();

  private final UUID id;
  private final UUID eventId;
  private final ExecutionStatus status;
  private final int attempt;
  private final Instant scheduledFor;
  private final Instant updatedAt;

  private Execution(UUID id, UUID eventId, ExecutionStatus status, int attempt, Instant scheduledFor,
      Instant updatedAt) {
    if (id == null) {
      throw new IllegalArgumentException("Execution id is required");
    }
    if (eventId == null) {
      throw new IllegalArgumentException("Event id is required for execution");
    }
    if (status == null) {
      throw new IllegalArgumentException("Execution status is required");
    }
    if (attempt < 1) {
      throw new IllegalArgumentException("Execution attempt must be positive");
    }
    if (scheduledFor == null) {
      throw new IllegalArgumentException("Execution schedule is required");
    }
    if (updatedAt == null) {
      throw new IllegalArgumentException("Execution update time is required");
    }
    this.id = id;
    this.eventId = eventId;
    this.status = status;
    this.attempt = attempt;
    this.scheduledFor = scheduledFor;
    this.updatedAt = updatedAt;
  }

  public static Execution initial(UUID eventId, Instant scheduledFor) {
    Instant now = scheduledFor == null ? Instant.now() : scheduledFor;
    return new Execution(UUID.randomUUID(), eventId, ExecutionStatus.PENDING, 1, now, now);
  }

  public UUID id() {
    return id;
  }

  public UUID eventId() {
    return eventId;
  }

  public ExecutionStatus status() {
    return status;
  }

  public int attempt() {
    return attempt;
  }

  public Instant scheduledFor() {
    return scheduledFor;
  }

  public Instant updatedAt() {
    return updatedAt;
  }

  public Execution start(Instant timestamp) {
    return transition(ExecutionStatus.RUNNING, timestamp, scheduledFor, attempt);
  }

  public Execution scheduleRetry(Duration delay, Instant timestamp) {
    if (delay == null) {
      throw new IllegalArgumentException("Retry delay is required");
    }
    Instant nextSchedule = scheduledFor.plus(delay);
    return transition(ExecutionStatus.WAITING_RETRY, timestamp, nextSchedule, attempt + 1);
  }

  public Execution markMaterializing(Instant timestamp) {
    return transition(ExecutionStatus.MATERIALIZING, timestamp, scheduledFor, attempt);
  }

  public Execution complete(Instant timestamp) {
    return transition(ExecutionStatus.COMPLETED, timestamp, scheduledFor, attempt);
  }

  public Execution fail(Instant timestamp) {
    return transition(ExecutionStatus.FAILED, timestamp, scheduledFor, attempt);
  }

  private Execution transition(ExecutionStatus next, Instant timestamp, Instant newSchedule,
      int nextAttempt) {
    if (next == null) {
      throw new IllegalArgumentException("Next status is required");
    }
    if (timestamp == null) {
      throw new IllegalArgumentException("Transition time is required");
    }
    if (!TRANSITIONS.getOrDefault(status, Set.of()).contains(next)) {
      throw new IllegalStateException("Transition from " + status + " to " + next + " is forbidden");
    }
    return new Execution(id, eventId, next, nextAttempt, newSchedule, timestamp);
  }

  private static Map<ExecutionStatus, Set<ExecutionStatus>> createTransitions() {
    Map<ExecutionStatus, Set<ExecutionStatus>> transitions = new EnumMap<>(ExecutionStatus.class);
    transitions.put(ExecutionStatus.PENDING, Set.of(ExecutionStatus.RUNNING));
    transitions.put(ExecutionStatus.RUNNING, Set.of(ExecutionStatus.MATERIALIZING,
        ExecutionStatus.COMPLETED, ExecutionStatus.FAILED, ExecutionStatus.WAITING_RETRY));
    transitions.put(ExecutionStatus.WAITING_RETRY, Set.of(ExecutionStatus.RUNNING));
    transitions.put(ExecutionStatus.MATERIALIZING, Set.of(ExecutionStatus.COMPLETED,
        ExecutionStatus.FAILED));
    transitions.put(ExecutionStatus.FAILED, Set.of(ExecutionStatus.WAITING_RETRY));
    transitions.put(ExecutionStatus.COMPLETED, Set.of());
    return transitions;
  }
}
