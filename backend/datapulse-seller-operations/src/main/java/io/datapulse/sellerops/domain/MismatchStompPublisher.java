package io.datapulse.sellerops.domain;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Publishes mismatch-specific STOMP events to
 * {@code /topic/workspace/{id}/mismatches} for user-initiated actions
 * (acknowledge, resolve, ignore).
 *
 * <p>Auto-detected mismatch events are pushed by
 * {@code WorkspaceAlertTopicStompPublisher} in datapulse-audit-alerting.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MismatchStompPublisher {

  private final SimpMessagingTemplate messagingTemplate;

  public void publishAcknowledged(long workspaceId, long mismatchId,
                                   String type, String severity,
                                   String offerName, BigDecimal deltaPct) {
    publishEvent(workspaceId, "MISMATCH_ACKNOWLEDGED",
        mismatchId, type, severity, offerName, deltaPct);
  }

  public void publishResolved(long workspaceId, long mismatchId,
                               String type, String severity,
                               String offerName, BigDecimal deltaPct) {
    publishEvent(workspaceId, "MISMATCH_RESOLVED",
        mismatchId, type, severity, offerName, deltaPct);
  }

  public void publishIgnored(long workspaceId, long mismatchId,
                              String type, String severity,
                              String offerName, BigDecimal deltaPct) {
    publishEvent(workspaceId, "MISMATCH_IGNORED",
        mismatchId, type, severity, offerName, deltaPct);
  }

  private void publishEvent(long workspaceId, String eventType,
                             long mismatchId, String type,
                             String severity, String offerName,
                             BigDecimal deltaPct) {
    try {
      String destination = "/topic/workspace/%d/mismatches".formatted(workspaceId);

      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("eventType", eventType);
      payload.put("mismatchId", mismatchId);
      payload.put("type", type);
      payload.put("severity", severity);
      payload.put("offerName", offerName);
      payload.put("deltaPct", deltaPct);

      messagingTemplate.convertAndSend(destination, payload);

      log.debug("Mismatch WS event sent: eventType={}, mismatchId={}, type={}",
          eventType, mismatchId, type);
    } catch (Exception e) {
      log.warn("Failed to send mismatch WS event: mismatchId={}, error={}",
          mismatchId, e.getMessage());
    }
  }
}
