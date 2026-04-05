package io.datapulse.api.websocket;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.api.config.RabbitTopologyConfig;
import io.datapulse.audit.domain.NotificationService;
import io.datapulse.audit.domain.NotificationType;
import io.datapulse.audit.domain.UserNotificationStompPublisher;
import io.datapulse.common.error.MessageCodes;
import io.datapulse.integration.persistence.MarketplaceConnectionEntity;
import io.datapulse.integration.persistence.MarketplaceConnectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Handles {@code ETL_SYNC_COMPLETED} from Rabbit: user notifications only. Workspace {@code sync-status}
 * is pushed from {@link ConnectionSyncHealthPushListener} (includes {@code ETL_JOB_COMPLETED} reason on
 * success) to avoid duplicate WebSocket messages.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SyncStatusPushListener {

    private final MarketplaceConnectionRepository connectionRepository;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;
    private final UserNotificationStompPublisher userNotificationStompPublisher;

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
                log.warn("ETL_SYNC_COMPLETED missing connectionId, skipping handler");
                return;
            }

            Long workspaceId = connectionRepository.findById(connectionId)
                    .map(MarketplaceConnectionEntity::getWorkspaceId)
                    .orElse(null);

            if (workspaceId == null) {
                log.warn("Connection not found for ETL_SYNC_COMPLETED: connectionId={}", connectionId);
                return;
            }

            if (shouldNotifySyncSuccess(payload)) {
                try {
                    List<long[]> created = notificationService.fanOut(
                            workspaceId,
                            null,
                            NotificationType.SYNC_COMPLETED.name(),
                            MessageCodes.INTEGRATION_NOTIFICATION_SYNC_COMPLETED_TITLE,
                            MessageCodes.INTEGRATION_NOTIFICATION_SYNC_COMPLETED_BODY,
                            "INFO");
                    userNotificationStompPublisher.publish(
                            created,
                            NotificationType.SYNC_COMPLETED.name(),
                            MessageCodes.INTEGRATION_NOTIFICATION_SYNC_COMPLETED_TITLE,
                            MessageCodes.INTEGRATION_NOTIFICATION_SYNC_COMPLETED_BODY,
                            "INFO",
                            null);
                } catch (Exception ex) {
                    log.warn(
                            "Sync-completed user notification fan-out failed: workspaceId={}, connectionId={}, error={}",
                            workspaceId,
                            connectionId,
                            ex.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Failed to handle ETL_SYNC_COMPLETED: error={}", e.getMessage(), e);
        }
    }

    /**
     * Notify only when payload explicitly lists zero failed domains. Missing or malformed
     * {@code failedDomains} is treated as non-success to avoid false-positive "sync completed" toasts.
     */
    private static boolean shouldNotifySyncSuccess(Map<String, Object> payload) {
        Object failed = payload.get("failedDomains");
        if (failed == null) {
            return false;
        }
        if (!(failed instanceof List<?> list)) {
            return false;
        }
        return list.isEmpty();
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }
}
