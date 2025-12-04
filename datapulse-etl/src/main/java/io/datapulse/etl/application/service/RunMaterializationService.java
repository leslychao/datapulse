package io.datapulse.etl.application.service;

import io.datapulse.etl.application.resolver.MaterializationStrategyResolver;
import io.datapulse.etl.application.resolver.MaterializationStrategyResolver.MaterializationStrategy;
import io.datapulse.etl.domain.entity.Event;
import io.datapulse.etl.domain.entity.MaterializationPlan;
import io.datapulse.etl.domain.event.MaterializationRequested;
import io.datapulse.etl.domain.repository.DomainEventOutboxRepository;
import io.datapulse.etl.domain.repository.EventRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RunMaterializationService {

  private final MaterializationStrategyResolver strategyResolver;
  private final EventRepository eventRepository;
  private final DomainEventOutboxRepository outboxRepository;

  public Optional<Event> materialize(UUID eventId, MaterializationPlan plan, Instant timestamp) {
    Optional<Event> eventOpt = eventRepository.findById(eventId);
    if (eventOpt.isEmpty()) {
      return Optional.empty();
    }
    Event event = eventOpt.orElseThrow();
    MaterializationStrategy strategy = strategyResolver.resolve(plan);
    strategy.execute(event, plan);
    outboxRepository.save(new MaterializationRequested(event.id(), plan, timestamp));
    Event updated = event.markMaterializationPending(timestamp);
    eventRepository.update(updated);
    return Optional.of(updated);
  }
}
