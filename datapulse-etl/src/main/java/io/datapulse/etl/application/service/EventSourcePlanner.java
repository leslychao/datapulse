package io.datapulse.etl.application.service;

import io.datapulse.etl.application.resolver.ExecutionPlanResolver;
import io.datapulse.etl.domain.entity.Event;
import io.datapulse.etl.domain.entity.MaterializationPlan;
import java.time.Instant;

public class EventSourcePlanner {

  private final ExecutionPlanResolver executionPlanResolver;

  public EventSourcePlanner(ExecutionPlanResolver executionPlanResolver) {
    this.executionPlanResolver = executionPlanResolver;
  }

  public MaterializationPlan plan(Event event, Instant timestamp) {
    if (event == null) {
      throw new IllegalArgumentException("Event is required for planning");
    }
    if (timestamp == null) {
      throw new IllegalArgumentException("Planning time is required");
    }
    return executionPlanResolver.resolve(event);
  }
}
