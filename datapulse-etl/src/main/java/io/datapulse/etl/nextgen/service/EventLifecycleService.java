package io.datapulse.etl.nextgen.service;

import io.datapulse.etl.nextgen.dto.EventStatus;
import io.datapulse.etl.nextgen.dto.ExecutionStatus;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class EventLifecycleService {

  private final Map<String, EventStatus> events = new ConcurrentHashMap<>();
  private final Map<String, OffsetDateTime> startedAt = new ConcurrentHashMap<>();
  private final Map<String, OffsetDateTime> finishedAt = new ConcurrentHashMap<>();
  private final Map<String, List<ExecutionStatus>> executionStatuses = new ConcurrentHashMap<>();

  public void start(String eventId) {
    events.put(eventId, EventStatus.IN_PROGRESS);
    startedAt.putIfAbsent(eventId, OffsetDateTime.now());
  }

  public void registerExecution(String eventId, ExecutionStatus status) {
    executionStatuses.computeIfAbsent(eventId, key -> new ArrayList<>()).add(status);
  }

  public EventStatus finalizeStatus(String eventId) {
    List<ExecutionStatus> statuses = executionStatuses.getOrDefault(eventId, List.of());
    if (statuses.isEmpty()) {
      events.put(eventId, EventStatus.NO_DATA);
      finishedAt.put(eventId, OffsetDateTime.now());
      return EventStatus.NO_DATA;
    }
    boolean hasSuccess = statuses.contains(ExecutionStatus.SUCCESS);
    boolean hasFailed = statuses.contains(ExecutionStatus.FAILED_FINAL);
    boolean allNoData = statuses.stream().allMatch(status -> status == ExecutionStatus.NO_DATA);
    boolean allFailed = statuses.stream().allMatch(status -> status == ExecutionStatus.FAILED_FINAL);
    EventStatus result;
    if (hasSuccess && hasFailed) {
      result = EventStatus.PARTIAL_SUCCESS;
    } else if (hasSuccess) {
      result = EventStatus.SUCCESS;
    } else if (allNoData) {
      result = EventStatus.NO_DATA;
    } else if (allFailed) {
      result = EventStatus.FAILED;
    } else {
      result = EventStatus.CANCELLED;
    }
    events.put(eventId, result);
    finishedAt.put(eventId, OffsetDateTime.now());
    return result;
  }

  public boolean isMaterializationAllowed(String eventId) {
    List<ExecutionStatus> statuses = executionStatuses.getOrDefault(eventId, List.of());
    if (statuses.isEmpty()) {
      return false;
    }
    EnumSet<ExecutionStatus> terminal = EnumSet.of(
        ExecutionStatus.SUCCESS,
        ExecutionStatus.NO_DATA,
        ExecutionStatus.FAILED_FINAL,
        ExecutionStatus.CANCELLED
    );
    boolean allTerminal = statuses.stream().allMatch(terminal::contains);
    boolean hasSuccess = statuses.contains(ExecutionStatus.SUCCESS);
    if (!allTerminal) {
      return false;
    }
    boolean allNoData = statuses.stream().allMatch(status -> status == ExecutionStatus.NO_DATA);
    boolean allFailed = statuses.stream().allMatch(status -> status == ExecutionStatus.FAILED_FINAL);
    return hasSuccess || (!allNoData && !allFailed);
  }

  public List<ExecutionStatus> executionStatuses(String eventId) {
    return executionStatuses.getOrDefault(eventId, List.of());
  }

  public EventStatus status(String eventId) {
    return events.getOrDefault(eventId, EventStatus.PENDING);
  }

  public OffsetDateTime startedAt(String eventId) {
    return startedAt.get(eventId);
  }

  public OffsetDateTime finishedAt(String eventId) {
    return finishedAt.get(eventId);
  }
}
