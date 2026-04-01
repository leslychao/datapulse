package io.datapulse.audit.domain;

import io.datapulse.audit.domain.event.AlertEventCreatedEvent;
import io.datapulse.audit.domain.event.AlertTriggeredEvent;
import io.datapulse.audit.persistence.AlertEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Listens for {@link AlertTriggeredEvent} (event-driven alerts from other modules)
 * and persists them to {@code alert_event} with {@code alert_rule_id = NULL}.
 *
 * <p>Uses {@code @EventListener} (not {@code @TransactionalEventListener}) so that
 * the alert is created even if the source transaction rolls back — operational alerts
 * are more important than transactional consistency.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertEventListener {

    private final AlertEventRepository alertEventRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Async("notificationExecutor")
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAlertTriggered(AlertTriggeredEvent event) {
        try {
            long alertEventId = alertEventRepository.insertEventDriven(
                    event.workspaceId(),
                    event.connectionId(),
                    event.severity(),
                    event.title(),
                    event.details(),
                    event.blocksAutomation()
            );
            log.info("Alert event created: alertEventId={}, severity={}, title={}",
                    alertEventId, event.severity(), event.title());

            eventPublisher.publishEvent(new AlertEventCreatedEvent(
                    alertEventId, event.workspaceId(), event.connectionId(),
                    null, event.severity(), event.title(), "OPEN",
                    event.blocksAutomation()));

        } catch (Exception e) {
            log.error("Failed to create alert event: severity={}, title={}, error={}",
                    event.severity(), event.title(), e.getMessage(), e);
        }
    }
}
