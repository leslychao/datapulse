package io.datapulse.etl.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
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
 * @param marketplace    WB, OZON, or YANDEX
 * @param credentials    raw Vault credentials map (apiToken for WB; clientId+apiKey for Ozon;
 *                       apiKey for Yandex)
 * @param eventType      FULL_SYNC, INCREMENTAL, MANUAL_SYNC, etc. (job_execution.event_type)
 * @param scope          subset of DAG nodes to run; full DAG unless MANUAL_SYNC with params.domains
 * @param checkpoint     parsed checkpoint from previous attempt (null on first attempt)
 * @param wbFactDateFrom start date (inclusive) for WB fact adapters using {@link LocalDate} filters
 * @param wbFactDateTo   end date (inclusive) for WB fact adapters, usually job-start calendar day
 * @param ozonFactSince      start instant for Ozon fact adapters ({@code since} parameter)
 * @param ozonFactTo         end instant for Ozon fact adapters ({@code to} parameter), typically
 *                           {@code now} at context build time
 * @param connectionMetadata raw JSON from {@code marketplace_connection.metadata} JSONB column;
 *                           contains provider-specific data discovered during health probe
 *                           (e.g. Yandex {@code businessId} and {@code campaigns})
 */
public record IngestContext(
        long jobExecutionId,
        long connectionId,
        long workspaceId,
        MarketplaceType marketplace,
        Map<String, String> credentials,
        String eventType,
        Set<EtlEventType> scope,
        Map<EtlEventType, CheckpointEntry> checkpoint,
        LocalDate wbFactDateFrom,
        LocalDate wbFactDateTo,
        OffsetDateTime ozonFactSince,
        OffsetDateTime ozonFactTo,
        String connectionMetadata
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

    /**
     * Resume token for a sub-source (adapter {@code sourceId}) within {@code event}, parsed from
     * checkpoint {@code last_cursor} (plain or {@link SubSourceCursorCodec} JSON).
     */
    public String resumeSubSourceCursor(EtlEventType event, String sourceId) {
        return SubSourceCursorCodec.resolve(resumeCursorFor(event), sourceId);
    }
}
