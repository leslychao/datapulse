package io.datapulse.integration.api;

/**
 * Semantics of a {@link WorkspaceSyncStatusPush} on {@code /topic/workspace/{id}/sync-status}.
 *
 * <p>{@link #STATE_CHANGED} — snapshot after DB sync-state mutation (scheduler, ingest).
 *
 * <p>{@link #ETL_JOB_COMPLETED} — terminal ingest success committed (with outbox completion event); same
 * snapshot plus client hint to refresh data-heavy queries (offers, analytics). Pushed from the
 * transactional {@code ConnectionSyncHealthInvalidatedEvent} path, not duplicated from Rabbit.
 */
public enum SyncStatusPushReason {
  STATE_CHANGED,
  ETL_JOB_COMPLETED
}
