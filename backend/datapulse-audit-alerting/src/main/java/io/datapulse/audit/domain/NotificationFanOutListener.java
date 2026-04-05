package io.datapulse.audit.domain;

import java.util.List;

import io.datapulse.audit.domain.event.AlertEventCreatedEvent;
import io.datapulse.audit.domain.event.AlertResolvedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

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
    private final UserNotificationStompPublisher userNotificationStompPublisher;
    private final WorkspaceAlertTopicStompPublisher workspaceAlertTopicStompPublisher;

    /**
     * AFTER_COMMIT: {@code user_notification} FK requires {@code alert_event} row visible
     * (publisher often runs inside an open transaction; async fan-out must not race commit).
     * {@code fallbackExecution} keeps tests and any non-transactional publisher paths working.
     */
    @Async("notificationExecutor")
    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAlertCreated(AlertEventCreatedEvent event) {
        try {
            workspaceAlertTopicStompPublisher.publishAlertCreated(event);

            List<long[]> notifications = notificationService.fanOut(
                    event.workspaceId(), event.alertEventId(),
                    NotificationType.ALERT.name(), event.title(), null,
                    event.severity());

            userNotificationStompPublisher.publish(
                    notifications,
                    NotificationType.ALERT.name(),
                    event.title(),
                    null,
                    event.severity(),
                    event.alertEventId());

            log.debug("Notification fan-out completed: alertEventId={}, notificationsCreated={}",
                    event.alertEventId(), notifications.size());

        } catch (Exception e) {
            log.error("Notification fan-out failed: alertEventId={}, error={}",
                    event.alertEventId(), e.getMessage(), e);
        }
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAlertResolved(AlertResolvedEvent event) {
        try {
            workspaceAlertTopicStompPublisher.publishAlertResolved(event);

            log.debug("Alert resolved push sent: alertEventId={}, resolvedReason={}",
                    event.alertEventId(), event.resolvedReason());

        } catch (Exception e) {
            log.error("Alert resolved push failed: alertEventId={}, error={}",
                    event.alertEventId(), e.getMessage(), e);
        }
    }

}

