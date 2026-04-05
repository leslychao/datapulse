package io.datapulse.api.websocket;

import io.datapulse.integration.domain.event.ConnectionStatusChangedEvent;
import io.datapulse.integration.persistence.MarketplaceConnectionEntity;
import io.datapulse.integration.persistence.MarketplaceConnectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Pushes connection status changes to workspace subscribers. Uses {@link TransactionPhase#AFTER_COMMIT}
 * so {@link MarketplaceConnectionRepository} sees committed rows when this runs {@link Async} (same
 * pattern as {@link ConnectionSyncHealthPushListener}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConnectionStatusPushListener {

  private final WorkspaceTopicStompPublisher workspaceTopicStompPublisher;
  private final MarketplaceConnectionRepository connectionRepository;

  @Async("notificationExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onConnectionStatusChanged(ConnectionStatusChangedEvent event) {
    try {
      Long workspaceId = connectionRepository.findById(event.connectionId())
          .map(MarketplaceConnectionEntity::getWorkspaceId)
          .orElse(null);

      if (workspaceId == null) {
        log.warn("Connection not found for WebSocket push: connectionId={}",
            event.connectionId());
        return;
      }

      workspaceTopicStompPublisher.publishConnectionStatusUpdate(
          workspaceId,
          event.connectionId(),
          event.newStatus(),
          event.trigger());

      log.debug("Connection status pushed: workspaceId={}, connectionId={}, newStatus={}",
          workspaceId, event.connectionId(), event.newStatus());
    } catch (Exception e) {
      log.error("Failed to push connection status via WebSocket: connectionId={}, error={}",
          event.connectionId(), e.getMessage(), e);
    }
  }
}
