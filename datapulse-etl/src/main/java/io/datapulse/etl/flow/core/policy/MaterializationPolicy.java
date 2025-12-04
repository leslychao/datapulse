package io.datapulse.etl.flow.core.policy;

import io.datapulse.etl.flow.core.model.EventAggregation;
import io.datapulse.etl.flow.core.model.EventStatus;
import io.datapulse.etl.flow.core.model.ExecutionStatus;

import org.springframework.stereotype.Component;

@Component
public final class MaterializationPolicy {

  public boolean readyForMaterialization(EventAggregation aggregation) {
    boolean allFinal = aggregation.executionStatuses().values().stream()
        .allMatch(this::isFinalStatus);
    boolean hasData = aggregation.hasData();

    if (!allFinal) {
      return false;
    }

    if (aggregation.status() == EventStatus.NO_DATA) {
      return false;
    }

    return hasData;
  }

  private boolean isFinalStatus(ExecutionStatus status) {
    return status == ExecutionStatus.SUCCESS
        || status == ExecutionStatus.NO_DATA
        || status == ExecutionStatus.ERROR;
  }
}
