package io.datapulse.api.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.api.config.RabbitTopologyConfig;
import io.datapulse.pricing.domain.PricingRunService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumes PRICING_RUN messages from RabbitMQ.
 *
 * <p>Error handling: unhandled exceptions are caught and logged (poison pill pattern).
 * Business-level retries are managed by {@link PricingRunService} internally.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PricingRunConsumer {

  private final PricingRunService pricingRunService;
  private final ObjectMapper objectMapper;

  @RabbitListener(queues = RabbitTopologyConfig.PRICING_RUN_QUEUE)
  public void onMessage(Message message) {
    try {
      JsonNode payload = objectMapper.readTree(message.getBody());
      long runId = payload.path("runId").asLong();

      if (runId <= 0) {
        log.error("Invalid runId in pricing.run message: payload={}",
            new String(message.getBody()));
        return;
      }

      log.info("Processing pricing run: runId={}", runId);
      pricingRunService.executeRun(runId);
    } catch (Exception e) {
      log.error("Poison pill detected in pricing.run queue: messageId={}, error={}",
          message.getMessageProperties().getMessageId(), e.getMessage(), e);
    }
  }
}
