package io.datapulse.api.execution;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.api.config.RabbitTopologyConfig;
import io.datapulse.promotions.domain.StaleCampaignListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class EtlEventsPricingConsumer {

  private final StaleCampaignListener staleCampaignListener;
  private final ObjectMapper objectMapper;

  @RabbitListener(queues = RabbitTopologyConfig.ETL_EVENTS_PRICING_QUEUE)
  public void onEtlEvent(Message message) {
    try {
      String eventType = extractHeader(message, "x-event-type");

      if ("ETL_PROMO_CAMPAIGN_STALE".equals(eventType)) {
        handleStaleCampaign(message);
      }
    } catch (Exception e) {
      log.error("Error processing ETL event on pricing queue: messageId={}, error={}",
          message.getMessageProperties().getMessageId(), e.getMessage(), e);
    }
  }

  private void handleStaleCampaign(Message message) throws Exception {
    Map<String, Object> payload = objectMapper.readValue(
        message.getBody(), new TypeReference<>() {});

    @SuppressWarnings("unchecked") // safe: Jackson deserializes JSON array to List<Integer>
    List<Number> rawIds = (List<Number>) payload.get("campaign_ids");

    if (rawIds == null || rawIds.isEmpty()) {
      log.warn("ETL_PROMO_CAMPAIGN_STALE missing campaign_ids, skipping");
      return;
    }

    List<Long> campaignIds = rawIds.stream().map(Number::longValue).toList();
    log.info("Processing stale campaigns: count={}", campaignIds.size());
    staleCampaignListener.onCampaignsStale(campaignIds);
  }

  private String extractHeader(Message message, String headerName) {
    Object value = message.getMessageProperties().getHeader(headerName);
    return value != null ? value.toString() : null;
  }
}
