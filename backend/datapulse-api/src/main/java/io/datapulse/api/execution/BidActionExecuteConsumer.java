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
        log.error("Invalid bidActionId in bid.execution message: "
            + "payload={}", new String(message.getBody()));
        return;
      }

      log.info("Processing bid action execution: bidActionId={}",
          bidActionId);
      executor.execute(bidActionId);
    } catch (Exception e) {
      log.error("Poison pill detected in bid.execution queue: "
              + "messageId={}, error={}",
          message.getMessageProperties().getMessageId(),
          e.getMessage(), e);
    }
  }
}
