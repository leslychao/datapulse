package io.datapulse.etl.application.service;

import io.datapulse.etl.application.resolver.MaterializationStrategyResolver;
import io.datapulse.etl.application.resolver.MaterializationStrategyResolver.MaterializationStrategy;
import io.datapulse.etl.application.resolver.NormalizationPipelineResolver;
import io.datapulse.etl.application.resolver.NormalizationPipelineResolver.NormalizationPipeline;
import io.datapulse.etl.domain.entity.Event;
import io.datapulse.etl.domain.entity.Execution;
import io.datapulse.etl.domain.entity.ExecutionStatus;
import io.datapulse.etl.domain.entity.MaterializationPlan;
import io.datapulse.etl.domain.entity.IngestionResult;
import io.datapulse.etl.domain.event.ExecutionCompleted;
import io.datapulse.etl.domain.event.ExecutionStatusChanged;
import io.datapulse.etl.domain.repository.DomainEventOutboxRepository;
import io.datapulse.etl.domain.repository.EventRepository;
import io.datapulse.etl.domain.repository.ExecutionRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ProcessExecutionService {

  private final EventRepository eventRepository;
  private final ExecutionRepository executionRepository;
  private final NormalizationPipelineResolver normalizationPipelineResolver;
  private final MaterializationStrategyResolver materializationStrategyResolver;
  private final DomainEventOutboxRepository outboxRepository;

  public Optional<Execution> process(UUID executionId, MaterializationPlan plan, Instant timestamp) {
    Optional<Execution> executionOpt = executionRepository.findById(executionId);
    if (executionOpt.isEmpty()) {
      return Optional.empty();
    }

    Execution execution = executionOpt.orElseThrow();
    Execution started = execution.start(timestamp);
    executionRepository.update(started);
    outboxRepository.save(new ExecutionStatusChanged(execution.id(), execution.status(),
        started.status(), timestamp));

    Event event = eventRepository.findById(execution.eventId()).orElseThrow();
    NormalizationPipeline pipeline = normalizationPipelineResolver.resolve(event);
    IngestionResult result = pipeline.execute(event);

    Execution next = result.rejectedItems() > 0
        ? started.fail(timestamp)
        : started.markMaterializing(timestamp);
    executionRepository.update(next);
    outboxRepository.save(new ExecutionStatusChanged(started.id(), started.status(),
        next.status(), timestamp));

    if (next.status() == ExecutionStatus.MATERIALIZING) {
      MaterializationStrategy strategy = materializationStrategyResolver.resolve(plan);
      strategy.execute(event, plan);
      Execution completed = next.complete(timestamp);
      executionRepository.update(completed);
      Event updatedEvent = event.markMaterializationPending(timestamp);
      eventRepository.update(updatedEvent);
      outboxRepository.save(new ExecutionCompleted(completed.id(), completed.status(), timestamp));
      outboxRepository.save(new ExecutionStatusChanged(next.id(), next.status(),
          completed.status(), timestamp));
    }
    return Optional.of(executionRepository.findById(executionId).orElseThrow());
  }
}
