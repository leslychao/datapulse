package io.datapulse.api.websocket;

import io.datapulse.integration.api.WorkspaceSyncStatusPush;
import io.datapulse.integration.domain.ConnectionSyncHealthService;
import io.datapulse.integration.domain.event.ConnectionSyncHealthInvalidatedEvent;
import io.datapulse.integration.persistence.MarketplaceConnectionEntity;
import io.datapulse.integration.persistence.MarketplaceConnectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConnectionSyncHealthPushListener {

    private final WorkspaceTopicStompPublisher workspaceTopicStompPublisher;
    private final MarketplaceConnectionRepository connectionRepository;
    private final ConnectionSyncHealthService connectionSyncHealthService;

    @Async("notificationExecutor")
    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true)
    public void onConnectionSyncHealthInvalidated(ConnectionSyncHealthInvalidatedEvent event) {
        try {
            Long workspaceId = connectionRepository
                    .findById(event.connectionId())
                    .map(MarketplaceConnectionEntity::getWorkspaceId)
                    .orElse(null);

            if (workspaceId == null) {
                log.warn(
                        "Connection not found for sync-status WebSocket push: connectionId={}",
                        event.connectionId());
                return;
            }

            connectionSyncHealthService
                    .summarize(event.connectionId())
                    .ifPresent(
                            dto ->
                                    workspaceTopicStompPublisher.publishSyncStatus(
                                            workspaceId,
                                            new WorkspaceSyncStatusPush(event.reason(), dto)));

            log.debug(
                    "Sync health pushed: workspaceId={}, connectionId={}",
                    workspaceId,
                    event.connectionId());
        } catch (Exception e) {
            log.error(
                    "Failed to push sync health via WebSocket: connectionId={}, error={}",
                    event.connectionId(),
                    e.getMessage(),
                    e);
        }
    }
}
