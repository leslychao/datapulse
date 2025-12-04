package io.datapulse.etl.application.service;

import io.datapulse.etl.application.resolver.RetryPolicyResolver;
import io.datapulse.etl.domain.entity.BackoffRequest;
import io.datapulse.etl.domain.entity.Execution;
import io.datapulse.etl.domain.entity.RetryDecision;
import io.datapulse.etl.domain.event.ExecutionRetryScheduled;
import io.datapulse.etl.domain.repository.EventRepository;
import io.datapulse.etl.domain.repository.DomainEventOutboxRepository;
import io.datapulse.etl.domain.repository.ExecutionRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ProcessRetryTimeoutService {

  private final ExecutionRepository executionRepository;
  private final RetryPolicyResolver retryPolicyResolver;
  private final DomainEventOutboxRepository outboxRepository;
  private final EventRepository eventRepository;

  public Optional<Execution> processRetry(UUID executionId, BackoffRequest request, Instant timestamp) {
    Optional<Execution> executionOpt = executionRepository.findById(executionId);
    if (executionOpt.isEmpty()) {
      return Optional.empty();
    }
    Execution execution = executionOpt.orElseThrow();
    RetryDecision decision = retryPolicyResolver.resolve(
            eventRepository.findById(execution.eventId()).orElseThrow())
        .decide(execution, request);
    if (decision.shouldRetry()) {
      Execution scheduled = execution.scheduleRetry(decision.retryDelay(), timestamp);
      executionRepository.update(scheduled);
      outboxRepository.save(new ExecutionRetryScheduled(scheduled.id(), scheduled.attempt(),
          decision.retryDelay(), timestamp));
      return Optional.of(scheduled);
    }
    Execution terminated = execution.fail(timestamp);
    executionRepository.update(terminated);
    return Optional.of(terminated);
  }
}
