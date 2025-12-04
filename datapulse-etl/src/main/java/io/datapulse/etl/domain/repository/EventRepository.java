package io.datapulse.etl.domain.repository;

import io.datapulse.etl.domain.entity.Event;
import java.util.Optional;
import java.util.UUID;

public interface EventRepository {

  Event save(Event event);

  Optional<Event> findById(UUID id);

  Event update(Event event);
}
