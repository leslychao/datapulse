package io.datapulse.etl.domain.repository;

import io.datapulse.etl.domain.event.DomainEvent;

public interface DomainEventOutboxRepository {

  void save(DomainEvent event);
}
