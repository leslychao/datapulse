package io.datapulse.etl.domain;

import java.util.Map;
import java.util.Set;

import io.datapulse.integration.domain.MarketplaceType;

/**
 * Immutable context for a single ETL ingest run.
 * Created once by {@link IngestOrchestrator} and passed down to DAG executor,
 * event runners, and sub-source runners.
 *
 * @param jobExecutionId job_execution PK
 * @param connectionId   marketplace_connection PK
 * @param workspaceId    workspace PK (resolved from connection)
 * @param marketplace    WB or OZON
 * @param credentials    raw Vault credentials map (apiToken for WB; clientId+apiKey for Ozon)
 * @param eventType      FULL_SYNC or INCREMENTAL
 * @param scope          which ETL events to run (all for FULL_SYNC, filtered for INCREMENTAL)
 * @param checkpoint     parsed checkpoint from previous attempt (null on first attempt)
 */
public record IngestContext(
        long jobExecutionId,
        long connectionId,
        long workspaceId,
        MarketplaceType marketplace,
        Map<String, String> credentials,
        String eventType,
        Set<EtlEventType> scope,
        Map<EtlEventType, CheckpointEntry> checkpoint
) {

    /**
     * Per-event checkpoint entry for DLX retry resume.
     */
    public record CheckpointEntry(
            EventResultStatus status,
            String lastCursor,
            String errorType,
            String error
    ) {}

    public boolean isRetry() {
        return checkpoint != null && !checkpoint.isEmpty();
    }

    /**
     * Whether this event was already completed in a previous attempt.
     */
    public boolean isEventCompleted(EtlEventType event) {
        if (checkpoint == null) {
            return false;
        }
        CheckpointEntry entry = checkpoint.get(event);
        return entry != null && entry.status() == EventResultStatus.COMPLETED;
    }

    /**
     * Returns resume cursor for a previously failed event (if available).
     */
    public String resumeCursorFor(EtlEventType event) {
        if (checkpoint == null) {
            return null;
        }
        CheckpointEntry entry = checkpoint.get(event);
        return entry != null ? entry.lastCursor() : null;
    }
}
