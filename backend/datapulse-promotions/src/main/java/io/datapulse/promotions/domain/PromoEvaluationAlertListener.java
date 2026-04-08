package io.datapulse.promotions.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.common.error.MessageCodes;
import io.datapulse.platform.audit.AlertTriggeredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Publishes a CRITICAL {@link AlertTriggeredEvent} when a promo evaluation run terminally fails.
 * Successful/in-progress runs are ignored.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PromoEvaluationAlertListener {

  private final ApplicationEventPublisher eventPublisher;
  private final ObjectMapper objectMapper;

  @Async("notificationExecutor")
  @EventListener
  public void onPromoEvaluationCompleted(PromoEvaluationCompletedEvent event) {
    if (event.status() != PromoRunStatus.FAILED) {
      return;
    }

    try {
      String details = buildDetails(event);

      eventPublisher.publishEvent(new AlertTriggeredEvent(
          event.workspaceId(),
          event.connectionId(),
          "CRITICAL",
          MessageCodes.ALERT_PROMO_EVALUATION_FAILED_TITLE,
          details,
          false));

      log.debug("Alert published for failed promo evaluation: runId={}, connectionId={}",
          event.runId(), event.connectionId());
    } catch (Exception e) {
      log.error("Failed to publish alert for promo evaluation failure: runId={}, error={}",
          event.runId(), e.getMessage(), e);
    }
  }

  private String buildDetails(PromoEvaluationCompletedEvent event) {
    try {
      return objectMapper.writeValueAsString(Map.of(
          "runId", event.runId(),
          "connectionId", event.connectionId(),
          "participateCount", event.participateCount(),
          "declineCount", event.declineCount(),
          "pendingReviewCount", event.pendingReviewCount(),
          "deactivateCount", event.deactivateCount()));
    } catch (JsonProcessingException e) {
      log.warn("Failed to serialize alert details for promo runId={}",
          event.runId(), e);
      return null;
    }
  }
}
