package io.datapulse.integration.api;

/**
 * Universal WebSocket payload for workspace sync UI: always carries the same health DTO as REST
 * {@code GET /api/connections/sync-health}, plus a reason for optional client-side actions.
 */
public record WorkspaceSyncStatusPush(
    SyncStatusPushReason reason, ConnectionSyncHealthResponse connection) {}
