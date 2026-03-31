package io.datapulse.api.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.platform.outbox.OutboxEvent;
import io.datapulse.platform.outbox.OutboxEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessagePropertiesBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public void publish(OutboxEvent event) {
        OutboxEventType type = event.getEventType();

        var propertiesBuilder = MessagePropertiesBuilder.newInstance()
                .setContentType("application/json")
                .setMessageId(String.valueOf(event.getId()))
                .setTimestamp(new Date())
                .setHeader("x-event-type", type.name())
                .setHeader("x-aggregate-type", event.getAggregateType())
                .setHeader("x-aggregate-id", event.getAggregateId());

        Long delayMs = extractDelayMs(event.getPayload());
        if (delayMs != null) {
            propertiesBuilder.setExpiration(String.valueOf(delayMs));
        }

        Message message = MessageBuilder
                .withBody(event.getPayload().getBytes(StandardCharsets.UTF_8))
                .andProperties(propertiesBuilder.build())
                .build();

        rabbitTemplate.send(type.getExchange(), type.getRoutingKey(), message);
    }

    private Long extractDelayMs(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            JsonNode delayNode = node.get("delay_ms");
            return delayNode != null && delayNode.isNumber() ? delayNode.longValue() : null;
        } catch (Exception e) {
            log.debug("Could not extract delay_ms from payload: {}", e.getMessage());
            return null;
        }
    }
}
