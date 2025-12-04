package io.datapulse.etl.application.service;

import io.datapulse.etl.application.resolver.ExecutionPlanResolver;
import io.datapulse.etl.domain.entity.Event;
import io.datapulse.etl.domain.entity.Execution;
import io.datapulse.etl.domain.entity.MaterializationPlan;
import io.datapulse.etl.domain.event.EventCreated;
import io.datapulse.etl.domain.event.MaterializationRequested;
import io.datapulse.etl.domain.repository.DomainEventOutboxRepository;
import io.datapulse.etl.domain.repository.EventRepository;
import io.datapulse.etl.domain.repository.ExecutionRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class StartEventSyncService {

  private final EventRepository eventRepository;
  private final ExecutionRepository executionRepository;
  private final ExecutionPlanResolver executionPlanResolver;
  private final DomainEventOutboxRepository outboxRepository;

  public Execution start(UUID eventId, String source, String payloadReference, Instant timestamp) {
    Event event = Event.received(eventId, source, payloadReference, timestamp);
    Event persisted = eventRepository.save(event);
    outboxRepository.save(new EventCreated(persisted.id(), persisted.createdAt()));

    MaterializationPlan plan = executionPlanResolver.resolve(persisted);
    outboxRepository.save(new MaterializationRequested(persisted.id(), plan, timestamp));

    Execution execution = Execution.initial(persisted.id(), timestamp);
    return executionRepository.save(execution);
  }
}
