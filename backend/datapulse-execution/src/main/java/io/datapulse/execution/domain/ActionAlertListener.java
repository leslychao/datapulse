package io.datapulse.execution.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.common.error.MessageCodes;
import io.datapulse.execution.domain.event.ActionFailedEvent;
import io.datapulse.platform.audit.AlertTriggeredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Publishes a CRITICAL {@link AlertTriggeredEvent} when a price action terminally fails.
 *
 * <p>Uses {@code @EventListener} (not {@code @TransactionalEventListener}) so the alert is
 * created even if the source transaction rolls back — operational visibility is prioritized
 * over strict atomicity (same rationale as {@code AlertEventListener}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActionAlertListener {

  private final ApplicationEventPublisher eventPublisher;
  private final ObjectMapper objectMapper;

  @Async("notificationExecutor")
  @EventListener
  public void onActionFailed(ActionFailedEvent event) {
    try {
      String details = buildDetails(event);

      eventPublisher.publishEvent(new AlertTriggeredEvent(
          event.workspaceId(),
          null,
          "CRITICAL",
          MessageCodes.ALERT_ACTION_FAILED_TITLE,
          details,
          false));

      log.debug("Alert published for failed action: actionId={}, error={}",
          event.actionId(), event.lastErrorClassification());
    } catch (Exception e) {
      log.error("Failed to publish alert for action failure: actionId={}, error={}",
          event.actionId(), e.getMessage(), e);
    }
  }

  private String buildDetails(ActionFailedEvent event) {
    try {
      return objectMapper.writeValueAsString(Map.of(
          "actionId", event.actionId(),
          "marketplaceOfferId", event.marketplaceOfferId(),
          "executionMode", event.executionMode().name(),
          "targetPrice", event.targetPrice(),
          "attemptCount", event.attemptCount(),
          "errorClassification", event.lastErrorClassification().name(),
          "lastErrorMessage", event.lastErrorMessage() != null
              ? event.lastErrorMessage() : ""));
    } catch (JsonProcessingException e) {
      log.warn("Failed to serialize alert details for actionId={}", event.actionId(), e);
      return null;
    }
  }
}
