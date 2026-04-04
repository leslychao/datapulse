package io.datapulse.audit.domain;

import java.util.Map;

import io.datapulse.audit.domain.event.AlertEventCreatedEvent;
import io.datapulse.audit.domain.event.AlertResolvedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/** Layer C: STOMP topic {@code /topic/workspace/{id}/alerts} for alert open/resolve broadcasts. */
@Service
@RequiredArgsConstructor
public class WorkspaceAlertTopicStompPublisher {

  private final SimpMessagingTemplate messagingTemplate;

  public void publishAlertCreated(AlertEventCreatedEvent event) {
    String destination = "/topic/workspace/%d/alerts".formatted(event.workspaceId());
    messagingTemplate.convertAndSend(
        destination,
        Map.of(
            "alertEventId", event.alertEventId(),
            "ruleType", String.valueOf(event.ruleType()),
            "severity", event.severity(),
            "title", event.title(),
            "status", event.status(),
            "connectionId", event.connectionId() != null ? event.connectionId() : ""));
  }

  public void publishAlertResolved(AlertResolvedEvent event) {
    String destination = "/topic/workspace/%d/alerts".formatted(event.workspaceId());
    messagingTemplate.convertAndSend(
        destination,
        Map.of(
            "alertEventId", event.alertEventId(),
            "severity", event.severity(),
            "title", event.title(),
            "status", "AUTO".equals(event.resolvedReason()) ? "AUTO_RESOLVED" : "RESOLVED",
            "resolvedReason", event.resolvedReason()));
  }
}
