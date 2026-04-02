package io.datapulse.api.websocket;

import java.util.Map;

import io.datapulse.integration.domain.event.ConnectionStatusChangedEvent;
import io.datapulse.integration.persistence.MarketplaceConnectionEntity;
import io.datapulse.integration.persistence.MarketplaceConnectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConnectionStatusPushListener {

  private final SimpMessagingTemplate messagingTemplate;
  private final MarketplaceConnectionRepository connectionRepository;

  @Async("notificationExecutor")
  @EventListener
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

      String destination =
          "/topic/workspace/%d/connection-updates".formatted(workspaceId);
      messagingTemplate.convertAndSend(destination, Map.of(
          "connectionId", event.connectionId(),
          "newStatus", event.newStatus(),
          "trigger", event.trigger()));

      log.debug("Connection status pushed: workspaceId={}, connectionId={}, newStatus={}",
          workspaceId, event.connectionId(), event.newStatus());
    } catch (Exception e) {
      log.error("Failed to push connection status via WebSocket: connectionId={}, error={}",
          event.connectionId(), e.getMessage(), e);
    }
  }
}
