package io.datapulse.platform.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Public API for creating outbox events.
 * Caller is responsible for wrapping in @Transactional so the event
 * is persisted atomically with the business data.
 */
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxEvent createEvent(OutboxEventType type, String aggregateType, long aggregateId, Object payload) {
        var event = new OutboxEvent();
        event.setEventType(type);
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setPayload(serializePayload(payload));
        event.setStatus(OutboxEventStatus.PENDING);
        return repository.save(event);
    }

    private String serializePayload(Object payload) {
        if (payload instanceof String s) {
            return s;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize outbox event payload", e);
        }
    }
}
