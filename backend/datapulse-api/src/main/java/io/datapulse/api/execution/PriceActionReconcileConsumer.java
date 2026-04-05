package io.datapulse.api.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.api.config.RabbitTopologyConfig;
import io.datapulse.execution.domain.ReconciliationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PriceActionReconcileConsumer {

    private final ReconciliationService reconciliationService;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitTopologyConfig.PRICE_RECONCILIATION_QUEUE)
    public void onMessage(Message message) {
        try {
            JsonNode payload = objectMapper.readTree(message.getBody());
            long actionId = payload.path("actionId").asLong();
            int attempt = payload.path("attempt").asInt(1);

            if (actionId <= 0) {
                log.error("Invalid actionId in reconciliation message: payload={}",
                        new String(message.getBody()));
                return;
            }

            reconciliationService.executeReconciliationCheck(actionId, attempt);
        } catch (Exception e) {
            log.error("Poison pill detected in price.reconciliation queue: messageId={}, error={}",
                    message.getMessageProperties().getMessageId(), e.getMessage(), e);
        }
    }
}
