package io.datapulse.audit.domain;

import java.util.List;
import java.util.Map;

import io.datapulse.audit.domain.event.AlertEventCreatedEvent;
import io.datapulse.audit.domain.event.AlertResolvedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Performs notification fan-out: when an alert event is created or resolved,
 * creates {@code user_notification} entries per workspace member and pushes
 * via WebSocket (STOMP).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationFanOutListener {

    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;

    @Async("notificationExecutor")
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAlertCreated(AlertEventCreatedEvent event) {
        try {
            pushAlertToWorkspace(event);

            List<long[]> notifications = notificationService.fanOut(
                    event.workspaceId(), event.alertEventId(),
                    NotificationType.ALERT.name(), event.title(), null,
                    event.severity());

            pushNotificationsToUsers(notifications, event);

            log.debug("Notification fan-out completed: alertEventId={}, notificationsCreated={}",
                    event.alertEventId(), notifications.size());

        } catch (Exception e) {
            log.error("Notification fan-out failed: alertEventId={}, error={}",
                    event.alertEventId(), e.getMessage(), e);
        }
    }

    @Async("notificationExecutor")
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAlertResolved(AlertResolvedEvent event) {
        try {
            String destination = "/topic/workspace/%d/alerts".formatted(event.workspaceId());
            messagingTemplate.convertAndSend(destination, Map.of(
                    "alertEventId", event.alertEventId(),
                    "severity", event.severity(),
                    "title", event.title(),
                    "status", "AUTO".equals(event.resolvedReason()) ? "AUTO_RESOLVED" : "RESOLVED",
                    "resolvedReason", event.resolvedReason()
            ));

            log.debug("Alert resolved push sent: alertEventId={}, resolvedReason={}",
                    event.alertEventId(), event.resolvedReason());

        } catch (Exception e) {
            log.error("Alert resolved push failed: alertEventId={}, error={}",
                    event.alertEventId(), e.getMessage(), e);
        }
    }

    private void pushAlertToWorkspace(AlertEventCreatedEvent event) {
        String destination = "/topic/workspace/%d/alerts".formatted(event.workspaceId());
        messagingTemplate.convertAndSend(destination, Map.of(
                "alertEventId", event.alertEventId(),
                "ruleType", String.valueOf(event.ruleType()),
                "severity", event.severity(),
                "title", event.title(),
                "status", event.status(),
                "connectionId", event.connectionId() != null ? event.connectionId() : ""
        ));
    }

    private void pushNotificationsToUsers(List<long[]> notifications, AlertEventCreatedEvent event) {
        for (long[] pair : notifications) {
            long userId = pair[0];
            long notificationId = pair[1];

            messagingTemplate.convertAndSendToUser(
                    String.valueOf(userId),
                    "/queue/notifications",
                    Map.of(
                            "notificationId", notificationId,
                            "notificationType", NotificationType.ALERT.name(),
                            "title", event.title(),
                            "severity", event.severity(),
                            "alertEventId", event.alertEventId()
                    ));
        }
    }
}
