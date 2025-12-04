package io.datapulse.etl.application.service;

import io.datapulse.etl.domain.entity.Event;
import io.datapulse.etl.domain.entity.EventSummary;
import io.datapulse.etl.domain.event.EventCompleted;
import io.datapulse.etl.domain.policy.EventFinalStatusPolicy;
import io.datapulse.etl.domain.repository.DomainEventOutboxRepository;
import io.datapulse.etl.domain.repository.EventRepository;
import io.datapulse.etl.domain.repository.EventSummaryRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EvaluateEventCompletionService {

  private final EventRepository eventRepository;
  private final EventSummaryRepository eventSummaryRepository;
  private final EventFinalStatusPolicy finalStatusPolicy;
  private final DomainEventOutboxRepository outboxRepository;

  public Optional<Event> evaluate(UUID eventId, Instant timestamp) {
    Optional<Event> eventOpt = eventRepository.findById(eventId);
    if (eventOpt.isEmpty()) {
      return Optional.empty();
    }
    Event event = eventOpt.orElseThrow();
    EventSummary summary = eventSummaryRepository.findByEventId(eventId).orElseThrow();
    Event updated = finalStatusPolicy.evaluate(event, summary, timestamp);
    eventRepository.update(updated);
    outboxRepository.save(new EventCompleted(updated.id(), updated.status(), timestamp));
    return Optional.of(updated);
  }
}
