package io.datapulse.etl.domain;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.datapulse.integration.domain.MarketplaceType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Auto-populated registry of all {@link EventSource} beans.
 * Spring injects every {@code EventSource} implementation via the constructor,
 * and this class indexes them by (marketplace, eventType) for O(1) lookup.
 */
@Slf4j
@Service
public class EventSourceRegistry {

    private final Map<EventSourceKey, EventSource> sources;

    public EventSourceRegistry(List<EventSource> allSources) {
        this.sources = allSources.stream()
                .collect(Collectors.toMap(
                        s -> new EventSourceKey(s.marketplace(), s.eventType()),
                        Function.identity()));

        log.info("EventSourceRegistry initialized: {} sources registered", sources.size());
    }

    public Optional<EventSource> resolve(MarketplaceType marketplace, EtlEventType eventType) {
        return Optional.ofNullable(sources.get(new EventSourceKey(marketplace, eventType)));
    }

    private record EventSourceKey(MarketplaceType marketplace, EtlEventType eventType) {}
}
