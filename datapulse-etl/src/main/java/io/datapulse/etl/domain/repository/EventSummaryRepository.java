package io.datapulse.etl.domain.repository;

import io.datapulse.etl.domain.entity.EventSummary;
import java.util.Optional;
import java.util.UUID;

public interface EventSummaryRepository {

  EventSummary save(EventSummary summary);

  Optional<EventSummary> findByEventId(UUID eventId);

  EventSummary update(EventSummary summary);
}
