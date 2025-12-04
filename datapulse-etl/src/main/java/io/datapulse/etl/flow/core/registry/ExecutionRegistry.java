package io.datapulse.etl.flow.core.registry;

import io.datapulse.etl.flow.core.model.EventAggregation;
import io.datapulse.etl.flow.core.model.EventStatus;
import io.datapulse.etl.flow.core.model.ExecutionDescriptor;
import io.datapulse.etl.flow.core.model.ExecutionOutcome;
import io.datapulse.etl.flow.core.model.ExecutionPlan;
import io.datapulse.etl.flow.core.model.ExecutionStatus;
import io.datapulse.etl.flow.core.policy.EventStatusPolicy;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class ExecutionRegistry {

  private record EventState(
      Long accountId,
      String event,
      LocalDate from,
      LocalDate to,
      Map<String, ExecutionStatus> statuses,
      Set<String> failedSources,
      boolean hasData
  ) {
  }

  private final Map<String, EventState> events = new ConcurrentHashMap<>();
  private final EventStatusPolicy statusPolicy = new EventStatusPolicy();

  public EventAggregation registerPlan(ExecutionPlan plan, String eventName) {
    Map<String, ExecutionStatus> statusMap = new HashMap<>();
    plan.executions().forEach(exec -> statusMap.put(exec.sourceId(), ExecutionStatus.PENDING));
    events.put(plan.requestId(), new EventState(
        plan.accountId(),
        eventName,
        plan.window().from(),
        plan.window().to(),
        statusMap,
        Collections.synchronizedSet(new HashSet<>()),
        false
    ));
    EventStatus eventStatus = statusPolicy.resolve(statusMap.values());
    return new EventAggregation(
        plan.requestId(),
        plan.accountId(),
        plan.executions().isEmpty() ? null : plan.executions().getFirst().event(),
        plan.window().from(),
        plan.window().to(),
        eventStatus,
        Map.copyOf(statusMap),
        Set.of(),
        false
    );
  }

  public EventAggregation update(ExecutionOutcome outcome) {
    EventState state = events.get(outcome.descriptor().requestId());
    if (state == null) {
      return null;
    }

    state.statuses().put(outcome.descriptor().sourceId(), outcome.status());
    if (outcome.status() == ExecutionStatus.ERROR) {
      state.failedSources().add(outcome.descriptor().sourceId());
    }
    if (outcome.status() == ExecutionStatus.SUCCESS) {
      state.hasData = true;
    }

    EventStatus eventStatus = statusPolicy.resolve(state.statuses().values());
    return new EventAggregation(
        outcome.descriptor().requestId(),
        state.accountId(),
        outcome.descriptor().event(),
        state.from(),
        state.to(),
        eventStatus,
        Map.copyOf(state.statuses()),
        Set.copyOf(state.failedSources()),
        state.hasData
    );
  }
}
