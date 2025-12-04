package io.datapulse.etl.flow.core.policy;

import io.datapulse.etl.flow.core.model.EventStatus;
import io.datapulse.etl.flow.core.model.ExecutionStatus;
import java.util.Collection;

public final class EventStatusPolicy {

  public EventStatus resolve(Collection<ExecutionStatus> statuses) {
    boolean hasInProgress = statuses.stream().anyMatch(status -> status == ExecutionStatus.IN_PROGRESS);
    boolean hasWaiting = statuses.stream().anyMatch(status -> status == ExecutionStatus.WAITING);
    boolean hasError = statuses.stream().anyMatch(status -> status == ExecutionStatus.ERROR);
    boolean hasSuccess = statuses.stream().anyMatch(status -> status == ExecutionStatus.SUCCESS);
    boolean hasNoData = statuses.stream().anyMatch(status -> status == ExecutionStatus.NO_DATA);

    if (hasInProgress || hasWaiting) {
      return hasWaiting ? EventStatus.WAITING : EventStatus.IN_PROGRESS;
    }

    if (hasError && hasSuccess) {
      return EventStatus.PARTIAL_SUCCESS;
    }

    if (hasError && !hasSuccess) {
      return EventStatus.ERROR;
    }

    if (hasSuccess && hasNoData) {
      return EventStatus.SUCCESS;
    }

    if (hasNoData && !hasSuccess) {
      return EventStatus.NO_DATA;
    }

    if (hasSuccess) {
      return EventStatus.SUCCESS;
    }

    return EventStatus.PENDING;
  }
}
