package io.datapulse.api.execution;

/**
 * Normalized {@code ETL_SYNC_COMPLETED} outbox / Rabbit payload fields used by API consumers.
 *
 * @param workspaceId tenant scope (from payload or resolved via {@code connectionId})
 * @param connectionId marketplace connection
 * @param jobExecutionId completed ingest job
 */
public record EtlSyncCompletedPayload(long workspaceId, long connectionId, long jobExecutionId) {}
