package io.datapulse.etl.domain.policy;

import io.datapulse.etl.domain.entity.Event;
import io.datapulse.etl.domain.entity.EventStatus;
import io.datapulse.etl.domain.entity.EventSummary;
import java.time.Instant;

public final class EventFinalStatusPolicy {

  public Event evaluate(Event event, EventSummary summary, Instant timestamp) {
    if (event == null || summary == null || timestamp == null) {
      throw new IllegalArgumentException("Event, summary and timestamp are required");
    }
    EventStatus nextStatus = summary.failedItems() == 0
        ? EventStatus.COMPLETED
        : EventStatus.FAILED;
    if (nextStatus == EventStatus.COMPLETED) {
      return event.complete(timestamp);
    }
    return event.fail(timestamp);
  }
}
