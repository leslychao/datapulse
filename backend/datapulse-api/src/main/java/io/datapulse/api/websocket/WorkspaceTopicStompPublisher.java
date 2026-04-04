package io.datapulse.api.websocket;

import java.util.Map;

import io.datapulse.integration.api.WorkspaceSyncStatusPush;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Layer C: all workspace-wide STOMP topic payloads under {@code /topic/workspace/{id}/...}
 * (except alert topics — those live in audit-alerting).
 */
@Service
@RequiredArgsConstructor
public class WorkspaceTopicStompPublisher {

  private final SimpMessagingTemplate messagingTemplate;

  public void publishConnectionStatusUpdate(
      long workspaceId, long connectionId, String newStatus, String trigger) {
    String destination = "/topic/workspace/%d/connection-updates".formatted(workspaceId);
    messagingTemplate.convertAndSend(
        destination,
        Map.of(
            "connectionId", connectionId,
            "newStatus", newStatus,
            "trigger", trigger));
  }

  public void publishSyncStatus(long workspaceId, WorkspaceSyncStatusPush payload) {
    String destination = "/topic/workspace/%d/sync-status".formatted(workspaceId);
    messagingTemplate.convertAndSend(destination, payload);
  }
}
