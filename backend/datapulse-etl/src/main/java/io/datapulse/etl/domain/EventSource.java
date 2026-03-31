package io.datapulse.etl.domain;

import java.util.List;

import io.datapulse.integration.domain.MarketplaceType;

/**
 * Strategy interface for a single ETL event pipeline bound to a specific marketplace.
 *
 * <p>Each implementation handles one (marketplace, eventType) pair:
 * captures raw pages via adapter, normalizes, upserts to canonical layer.</p>
 *
 * <p>Implementations are Spring beans automatically discovered by
 * {@link EventSourceRegistry} through {@code List<EventSource>} injection.</p>
 */
public interface EventSource {

    MarketplaceType marketplace();

    EtlEventType eventType();

    /**
     * Executes the full capture → normalize → UPSERT pipeline for this event.
     * May contain multiple sequential sub-sources (e.g., Ozon PRODUCT_DICT
     * has list → info → attributes).
     *
     * @param context immutable ingest context with credentials and job metadata
     * @return one result per sub-source executed
     */
    List<SubSourceResult> execute(IngestContext context);
}
