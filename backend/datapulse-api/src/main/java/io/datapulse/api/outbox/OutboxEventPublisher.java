package io.datapulse.api.outbox;

import io.datapulse.platform.outbox.OutboxEvent;
import io.datapulse.platform.outbox.OutboxEventType;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessagePropertiesBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
@RequiredArgsConstructor
public class OutboxEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publish(OutboxEvent event) {
        OutboxEventType type = event.getEventType();

        var properties = MessagePropertiesBuilder.newInstance()
                .setContentType("application/json")
                .setMessageId(String.valueOf(event.getId()))
                .setTimestamp(new Date())
                .setHeader("x-event-type", type.name())
                .setHeader("x-aggregate-type", event.getAggregateType())
                .setHeader("x-aggregate-id", event.getAggregateId())
                .build();

        Message message = MessageBuilder
                .withBody(event.getPayload().getBytes(StandardCharsets.UTF_8))
                .andProperties(properties)
                .build();

        rabbitTemplate.send(type.getExchange(), type.getRoutingKey(), message);
    }
}
