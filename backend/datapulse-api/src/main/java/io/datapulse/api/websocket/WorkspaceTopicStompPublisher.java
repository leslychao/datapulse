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

  public void publishBiddingRunUpdate(long workspaceId, Map<String, Object> payload) {
    messagingTemplate.convertAndSend(
        "/topic/workspace/%d/bidding-runs".formatted(workspaceId), payload);
  }

  public void publishActionStatusUpdate(
      long workspaceId, long actionId, String status, String executionMode) {
    Map<String, Object> payload = Map.of(
        "actionId", actionId,
        "status", status,
        "executionMode", executionMode);

    messagingTemplate.convertAndSend(
        "/topic/workspace/%d/actions".formatted(workspaceId), payload);
    messagingTemplate.convertAndSend(
        "/topic/workspace/%d/actions/%d".formatted(workspaceId, actionId), payload);
    messagingTemplate.convertAndSend(
        "/topic/workspace/%d/action-updates".formatted(workspaceId), payload);
  }
}
