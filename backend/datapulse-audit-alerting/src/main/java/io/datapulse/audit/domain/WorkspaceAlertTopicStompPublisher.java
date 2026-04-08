package io.datapulse.audit.domain;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.audit.domain.event.AlertEventCreatedEvent;
import io.datapulse.audit.domain.event.AlertResolvedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * STOMP topic publisher for alert broadcasts.
 * <ul>
 *   <li>{@code /topic/workspace/{id}/alerts} — all alerts</li>
 *   <li>{@code /topic/workspace/{id}/mismatches} — mismatch-specific events</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkspaceAlertTopicStompPublisher {

  private final SimpMessagingTemplate messagingTemplate;
  private final ObjectMapper objectMapper;

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

    publishMismatchDetectedIfApplicable(event);
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

  private void publishMismatchDetectedIfApplicable(AlertEventCreatedEvent event) {
    if (event.details() == null) {
      return;
    }
    try {
      JsonNode details = objectMapper.readTree(event.details());
      JsonNode typeNode = details.get("mismatch_type");
      if (typeNode == null || typeNode.isNull()) {
        return;
      }

      String mismatchDest = "/topic/workspace/%d/mismatches".formatted(event.workspaceId());

      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("eventType", "MISMATCH_DETECTED");
      payload.put("mismatchId", event.alertEventId());
      payload.put("type", typeNode.asText());
      payload.put("severity", event.severity());
      payload.put("offerName", details.has("offer_name")
          ? details.get("offer_name").asText() : null);
      payload.put("deltaPct", details.has("delta_pct") && !details.get("delta_pct").isNull()
          ? details.get("delta_pct").asText() : null);

      messagingTemplate.convertAndSend(mismatchDest, payload);

      log.debug("Mismatch detected WS event sent: mismatchId={}, type={}",
          event.alertEventId(), typeNode.asText());
    } catch (Exception e) {
      log.warn("Failed to publish mismatch WS event: alertEventId={}, error={}",
          event.alertEventId(), e.getMessage());
    }
  }
}
