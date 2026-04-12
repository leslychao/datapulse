package io.datapulse.api.execution;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.api.config.RabbitTopologyConfig;
import io.datapulse.execution.domain.ActionExecutionMode;
import io.datapulse.execution.domain.ActionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumes PRICING_ACTION_REQUESTED events published by PricingActionScheduler.
 * Creates PriceAction via ActionService (handles supersede, defer, auto-approve).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PricingActionMaterializerConsumer {

  private final ActionService actionService;
  private final ObjectMapper objectMapper;

  @RabbitListener(queues = RabbitTopologyConfig.PRICING_ACTION_MATERIALIZE_QUEUE)
  public void onMessage(Message message) {
    try {
      JsonNode payload = objectMapper.readTree(message.getBody());

      long decisionId = payload.path("decisionId").asLong();
      long marketplaceOfferId = payload.path("marketplaceOfferId").asLong();
      long workspaceId = payload.path("workspaceId").asLong();
      BigDecimal targetPrice = new BigDecimal(payload.path("targetPrice").asText("0"));
      BigDecimal currentPrice = new BigDecimal(payload.path("currentPrice").asText("0"));
      boolean autoApprove = payload.path("autoApprove").asBoolean(false);
      int approvalTimeoutHours = payload.path("approvalTimeoutHours").asInt(72);
      String executionModeStr = payload.path("executionMode").asText("SEMI_AUTO");

      if (decisionId <= 0 || marketplaceOfferId <= 0) {
        log.error("Invalid payload in PRICING_ACTION_REQUESTED: payload={}",
            new String(message.getBody()));
        return;
      }

      ActionExecutionMode actionMode = "SIMULATED".equals(executionModeStr)
          ? ActionExecutionMode.SIMULATED
          : ActionExecutionMode.LIVE;

      actionService.createAction(
          workspaceId,
          marketplaceOfferId,
          decisionId,
          actionMode,
          targetPrice,
          currentPrice,
          approvalTimeoutHours,
          autoApprove);

      log.info("Pricing action materialized: decisionId={}, offerId={}, autoApprove={}",
          decisionId, marketplaceOfferId, autoApprove);

    } catch (Exception e) {
      log.error("Failed to materialize pricing action: messageId={}, error={}",
          message.getMessageProperties().getMessageId(), e.getMessage(), e);
    }
  }
}
