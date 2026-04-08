package io.datapulse.pricing.domain;

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
 * Publishes a CRITICAL {@link AlertTriggeredEvent} when a pricing run terminally fails.
 * Successful/cancelled runs are ignored.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PricingRunAlertListener {

  private final ApplicationEventPublisher eventPublisher;
  private final ObjectMapper objectMapper;

  @Async("notificationExecutor")
  @EventListener
  public void onPricingRunCompleted(PricingRunCompletedEvent event) {
    if (event.finalStatus() != RunStatus.FAILED) {
      return;
    }

    try {
      String details = buildDetails(event);

      eventPublisher.publishEvent(new AlertTriggeredEvent(
          event.workspaceId(),
          event.connectionId(),
          "CRITICAL",
          MessageCodes.ALERT_PRICING_RUN_FAILED_TITLE,
          details,
          false));

      log.debug("Alert published for failed pricing run: pricingRunId={}, connectionId={}",
          event.pricingRunId(), event.connectionId());
    } catch (Exception e) {
      log.error("Failed to publish alert for pricing run failure: pricingRunId={}, error={}",
          event.pricingRunId(), e.getMessage(), e);
    }
  }

  private String buildDetails(PricingRunCompletedEvent event) {
    try {
      return objectMapper.writeValueAsString(Map.of(
          "pricingRunId", event.pricingRunId(),
          "connectionId", event.connectionId(),
          "changeCount", event.changeCount(),
          "skipCount", event.skipCount(),
          "holdCount", event.holdCount()));
    } catch (JsonProcessingException e) {
      log.warn("Failed to serialize alert details for pricingRunId={}",
          event.pricingRunId(), e);
      return null;
    }
  }
}
