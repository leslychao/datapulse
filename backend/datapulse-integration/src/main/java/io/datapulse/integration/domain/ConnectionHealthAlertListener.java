package io.datapulse.integration.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.common.error.MessageCodes;
import io.datapulse.integration.domain.event.ConnectionHealthDegradedEvent;
import io.datapulse.platform.audit.AlertTriggeredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Publishes a WARNING {@link AlertTriggeredEvent} when a connection's health degrades
 * (N consecutive health-check failures).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConnectionHealthAlertListener {

  private final ApplicationEventPublisher eventPublisher;
  private final ObjectMapper objectMapper;

  @Async("notificationExecutor")
  @EventListener
  public void onHealthDegraded(ConnectionHealthDegradedEvent event) {
    try {
      String details = buildDetails(event);

      eventPublisher.publishEvent(new AlertTriggeredEvent(
          event.workspaceId(),
          event.connectionId(),
          "WARNING",
          MessageCodes.ALERT_CONNECTION_HEALTH_DEGRADED_TITLE,
          details,
          false));

      log.debug("Alert published for degraded connection: connectionId={}, failures={}",
          event.connectionId(), event.consecutiveFailures());
    } catch (Exception e) {
      log.error("Failed to publish health-degraded alert: connectionId={}, error={}",
          event.connectionId(), e.getMessage(), e);
    }
  }

  private String buildDetails(ConnectionHealthDegradedEvent event) {
    try {
      return objectMapper.writeValueAsString(Map.of(
          "connectionId", event.connectionId(),
          "marketplaceType", event.marketplaceType(),
          "consecutiveFailures", event.consecutiveFailures(),
          "lastErrorCode", event.lastErrorCode() != null
              ? event.lastErrorCode() : ""));
    } catch (JsonProcessingException e) {
      log.warn("Failed to serialize alert details for connectionId={}",
          event.connectionId(), e);
      return null;
    }
  }
}
