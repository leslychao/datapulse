package io.datapulse.api.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.api.config.RabbitTopologyConfig;
import io.datapulse.bidding.domain.BiddingRunService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BiddingRunConsumer {

  private final BiddingRunService biddingRunService;
  private final ObjectMapper objectMapper;

  @RabbitListener(queues = RabbitTopologyConfig.BIDDING_RUN_QUEUE)
  public void onMessage(Message message) {
    try {
      JsonNode payload = objectMapper.readTree(message.getBody());
      long workspaceId = payload.path("workspaceId").asLong();
      long bidPolicyId = payload.path("bidPolicyId").asLong();

      if (workspaceId <= 0 || bidPolicyId <= 0) {
        log.error("Invalid payload in bidding.run message: payload={}",
            new String(message.getBody()));
        return;
      }

      log.info("Processing bidding run: workspaceId={}, bidPolicyId={}",
          workspaceId, bidPolicyId);
      biddingRunService.executeRun(workspaceId, bidPolicyId);
    } catch (Exception e) {
      log.error("Poison pill detected in bidding.run queue: messageId={}, error={}",
          message.getMessageProperties().getMessageId(), e.getMessage(), e);
    }
  }
}
