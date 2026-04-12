package io.datapulse.api.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.api.config.RabbitTopologyConfig;
import io.datapulse.bidding.domain.BidActionExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Unified consumer for all bid execution messages on {@code bid.execution} queue.
 * <p>
 * Three message types arrive here:
 * <ul>
 *   <li>{@code BID_ACTION_EXECUTE} — initial execution, published directly</li>
 *   <li>{@code BID_ACTION_RECONCILE} — reconciliation check, published directly</li>
 *   <li>{@code BID_ACTION_RETRY} — retry after delay, arrives via DLX
 *       from {@code bid.execution.wait} after TTL expiry</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BidActionExecuteConsumer {

  private final BidActionExecutor executor;
  private final ObjectMapper objectMapper;

  @RabbitListener(queues = RabbitTopologyConfig.BID_EXECUTION_QUEUE)
  public void onMessage(Message message) {
    try {
      JsonNode payload = objectMapper.readTree(message.getBody());
      long bidActionId = payload.path("bidActionId").asLong();

      if (bidActionId <= 0) {
        log.error("Invalid bidActionId in bid.execution message: payload={}",
            new String(message.getBody()));
        return;
      }

      String eventType = payload.path("eventType").asText("");

      if ("RECONCILE".equals(eventType)) {
        log.info("Processing bid action reconciliation: bidActionId={}",
            bidActionId);
        executor.reconcile(bidActionId);
      } else {
        int attemptNumber = payload.path("attemptNumber").asInt(1);
        log.info("Processing bid action: bidActionId={}, eventType={}, attempt={}",
            bidActionId, eventType, attemptNumber);
        executor.execute(bidActionId);
      }
    } catch (Exception e) {
      log.error("Poison pill detected in bid.execution queue: messageId={}, error={}",
          message.getMessageProperties().getMessageId(),
          e.getMessage(), e);
    }
  }
}
