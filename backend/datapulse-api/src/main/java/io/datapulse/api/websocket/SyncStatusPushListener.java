package io.datapulse.api.websocket;

import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.api.config.RabbitTopologyConfig;
import io.datapulse.integration.persistence.MarketplaceConnectionEntity;
import io.datapulse.integration.persistence.MarketplaceConnectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SyncStatusPushListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final MarketplaceConnectionRepository connectionRepository;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitTopologyConfig.ETL_EVENTS_API_QUEUE)
    public void onEtlEvent(Message message) {
        try {
            String eventType = message.getMessageProperties().getHeader("x-event-type");
            if (!"ETL_SYNC_COMPLETED".equals(eventType)) {
                return;
            }

            Map<String, Object> payload = objectMapper.readValue(
                    message.getBody(), new TypeReference<>() {});

            Long connectionId = toLong(payload.get("connectionId"));
            if (connectionId == null) {
                log.warn("ETL_SYNC_COMPLETED missing connectionId, skipping WebSocket push");
                return;
            }

            Long workspaceId = connectionRepository.findById(connectionId)
                    .map(MarketplaceConnectionEntity::getWorkspaceId)
                    .orElse(null);

            if (workspaceId == null) {
                log.warn("Connection not found for WebSocket push: connectionId={}", connectionId);
                return;
            }

            String destination = "/topic/workspace/%d/sync-status".formatted(workspaceId);
            messagingTemplate.convertAndSend(destination, payload);

            log.debug("Sync status pushed: workspaceId={}, connectionId={}, destination={}",
                    workspaceId, connectionId, destination);
        } catch (Exception e) {
            log.error("Failed to push sync status via WebSocket: error={}", e.getMessage(), e);
        }
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }
}
