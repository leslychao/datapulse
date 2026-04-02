package io.datapulse.api.websocket;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.api.config.RabbitTopologyConfig;
import io.datapulse.audit.domain.NotificationService;
import io.datapulse.audit.domain.NotificationType;
import io.datapulse.common.error.MessageCodes;
import io.datapulse.integration.persistence.MarketplaceConnectionEntity;
import io.datapulse.integration.persistence.MarketplaceConnectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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

    private final SimpMessagingTemplate messagingTemplate;
    private final MarketplaceConnectionRepository connectionRepository;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;

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
                    pushSyncNotificationsToUsers(created);
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

    private void pushSyncNotificationsToUsers(List<long[]> userIdNotificationIdPairs) {
        String createdAt = OffsetDateTime.now().toString();
        for (long[] pair : userIdNotificationIdPairs) {
            long userId = pair[0];
            long notificationId = pair[1];
            messagingTemplate.convertAndSendToUser(
                    String.valueOf(userId),
                    "/queue/notifications",
                    syncCompletedNotificationPayload(notificationId, createdAt));
        }
    }

    private static Map<String, Object> syncCompletedNotificationPayload(
            long notificationId, String createdAt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", notificationId);
        body.put("notificationId", notificationId);
        body.put("notificationType", NotificationType.SYNC_COMPLETED.name());
        body.put("alertEventId", null);
        body.put("severity", "INFO");
        body.put("title", MessageCodes.INTEGRATION_NOTIFICATION_SYNC_COMPLETED_TITLE);
        body.put("body", MessageCodes.INTEGRATION_NOTIFICATION_SYNC_COMPLETED_BODY);
        body.put("createdAt", createdAt);
        body.put("read", false);
        return body;
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }
}
