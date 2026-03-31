package io.datapulse.etl.domain;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Thin dispatcher: resolves the {@link EventSource} for the given
 * (marketplace, eventType) pair via {@link EventSourceRegistry} and delegates execution.
 *
 * <p>All marketplace-specific logic lives in individual {@code EventSource} implementations
 * under {@code io.datapulse.etl.domain.source.wb} and {@code io.datapulse.etl.domain.source.ozon}.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventRunner {

    private final EventSourceRegistry registry;

    /**
     * Executes a single ETL event for the given context.
     * Looks up the appropriate {@link EventSource} and delegates.
     */
    public EventResult run(EtlEventType eventType, IngestContext context) {
        log.info("Event started: eventType={}, connectionId={}, jobExecutionId={}",
                eventType, context.connectionId(), context.jobExecutionId());

        try {
            EventSource source = registry.resolve(context.marketplace(), eventType)
                    .orElseThrow(() -> new IllegalStateException(
                            "No EventSource registered for %s/%s".formatted(context.marketplace(), eventType)));

            List<SubSourceResult> results = source.execute(context);

            EventResult result = EventResult.fromSubSources(eventType, results);

            log.info("Event finished: eventType={}, status={}, connectionId={}",
                    eventType, result.status(), context.connectionId());
            return result;
        } catch (Exception e) {
            log.error("Event failed with unexpected error: eventType={}, connectionId={}, error={}",
                    eventType, context.connectionId(), e.getMessage(), e);
            return EventResult.failed(eventType, List.of(SubSourceResult.failed(
                    eventType.name(), e.getMessage())));
        }
    }
}
