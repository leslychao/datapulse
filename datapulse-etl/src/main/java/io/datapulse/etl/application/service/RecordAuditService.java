package io.datapulse.etl.application.service;

import io.datapulse.etl.domain.event.DomainEvent;
import io.datapulse.etl.domain.repository.DomainEventOutboxRepository;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class RecordAuditService {

  private final DomainEventOutboxRepository outboxRepository;

  public void record(DomainEvent event) {
    if (event == null) {
      throw new IllegalArgumentException("Audit event is required");
    }
    outboxRepository.save(event);
  }
}
