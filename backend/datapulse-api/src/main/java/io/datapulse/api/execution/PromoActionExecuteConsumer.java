package io.datapulse.api.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.api.config.RabbitTopologyConfig;
import io.datapulse.promotions.domain.PromoActionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumes PROMO_EXECUTION messages from RabbitMQ.
 *
 * <p>Error handling: unhandled exceptions are caught and logged (poison pill pattern).
 * Business-level retries are managed by {@link PromoActionService} internally.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PromoActionExecuteConsumer {

  private final PromoActionService promoActionService;
  private final ObjectMapper objectMapper;

  @RabbitListener(queues = RabbitTopologyConfig.PROMO_EXECUTION_QUEUE)
  public void onMessage(Message message) {
    try {
      JsonNode payload = objectMapper.readTree(message.getBody());
      long actionId = payload.path("actionId").asLong();

      if (actionId <= 0) {
        log.error("Invalid actionId in promo.execution message: payload={}",
            new String(message.getBody()));
        return;
      }

      log.info("Processing promo action execution: actionId={}", actionId);
      promoActionService.executeAction(actionId);
    } catch (Exception e) {
      log.error("Poison pill detected in promo.execution queue: messageId={}, error={}",
          message.getMessageProperties().getMessageId(), e.getMessage(), e);
    }
  }
}
