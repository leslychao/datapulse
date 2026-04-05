package io.datapulse.integration.domain.event;

import io.datapulse.integration.api.SyncStatusPushReason;

/**
 * Published after {@code marketplace_sync_state} changes so the API can push
 * {@link io.datapulse.integration.api.WorkspaceSyncStatusPush} to
 * {@code /topic/workspace/{id}/sync-status}.
 *
 * <p>Use {@link SyncStatusPushReason#ETL_JOB_COMPLETED} when a terminal ingest success is committed
 * (same transaction as outbox {@code ETL_SYNC_COMPLETED}) so the client gets one message with both
 * the health snapshot and the hint to refresh heavy queries — without a duplicate push from Rabbit.
 */
public record ConnectionSyncHealthInvalidatedEvent(long connectionId, SyncStatusPushReason reason) {}
