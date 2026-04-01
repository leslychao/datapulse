package io.datapulse.api.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.api.config.RabbitTopologyConfig;
import io.datapulse.execution.domain.PriceActionExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumes PRICE_ACTION_EXECUTE and PRICE_ACTION_RETRY messages from RabbitMQ.
 *
 * Consumer error handling per execution.md:
 * - defaultRequeueRejected=false → unhandled exceptions ACK the message (no requeue)
 * - Retriable business errors handled inside PriceActionExecutor via outbox retry
 * - Poison pills: message consumed, error logged, investigation via outbox_event.last_error
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PriceActionExecuteConsumer {

    private final PriceActionExecutor executor;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitTopologyConfig.PRICE_EXECUTION_QUEUE)
    public void onMessage(Message message) {
        String eventType = extractHeader(message, "x-event-type");

        try {
            JsonNode payload = objectMapper.readTree(message.getBody());
            long actionId = payload.path("actionId").asLong();

            if (actionId <= 0) {
                log.error("Invalid actionId in execution message: payload={}", new String(message.getBody()));
                return;
            }

            if ("PRICE_ACTION_RETRY".equals(eventType)) {
                int attemptNumber = payload.path("attemptNumber").asInt(1);
                log.info("Processing retry: actionId={}, attempt={}", actionId, attemptNumber);
                executor.executeRetry(actionId, attemptNumber);
            } else {
                log.info("Processing execution: actionId={}", actionId);
                executor.execute(actionId);
            }
        } catch (Exception e) {
            log.error("Poison pill detected in price.execution queue: messageId={}, error={}",
                    message.getMessageProperties().getMessageId(), e.getMessage(), e);
        }
    }

    private String extractHeader(Message message, String headerName) {
        Object value = message.getMessageProperties().getHeader(headerName);
        return value != null ? value.toString() : null;
    }
}
