package io.datapulse.etl.flow.core.model;

import java.util.List;
import java.util.Set;

public record ExecutionPlan(
    String requestId,
    Long accountId,
    EventWindow window,
    List<ExecutionDescriptor> executions
) {
  public Set<String> sourceIds() {
    return executions.stream().map(ExecutionDescriptor::sourceId).collect(java.util.stream.Collectors.toUnmodifiableSet());
  }
}
