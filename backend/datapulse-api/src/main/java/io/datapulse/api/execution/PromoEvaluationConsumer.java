package io.datapulse.api.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.api.config.RabbitTopologyConfig;
import io.datapulse.promotions.domain.PromoEvaluationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumes PROMO_EVALUATION messages from RabbitMQ.
 *
 * <p>Error handling: unhandled exceptions are caught and logged (poison pill pattern).
 * Business-level retries are managed by {@link PromoEvaluationService} internally.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PromoEvaluationConsumer {

  private final PromoEvaluationService promoEvaluationService;
  private final ObjectMapper objectMapper;

  @RabbitListener(queues = RabbitTopologyConfig.PROMO_EVALUATION_QUEUE)
  public void onMessage(Message message) {
    try {
      JsonNode payload = objectMapper.readTree(message.getBody());
      long connectionId = payload.path("connectionId").asLong();
      long workspaceId = payload.path("workspaceId").asLong();

      if (connectionId <= 0 || workspaceId <= 0) {
        log.error("Invalid connectionId/workspaceId in promo.evaluation message: payload={}",
            new String(message.getBody()));
        return;
      }

      log.info("Processing promo evaluation: connectionId={}, workspaceId={}",
          connectionId, workspaceId);
      promoEvaluationService.evaluate(connectionId, workspaceId);
    } catch (Exception e) {
      log.error("Poison pill detected in promo.evaluation queue: messageId={}, payload={}, error={}",
          message.getMessageProperties().getMessageId(),
          new String(message.getBody()), e.getMessage(), e);
    }
  }
}
