package io.datapulse.audit.domain;

import io.datapulse.audit.domain.event.AlertTriggeredEvent;
import io.datapulse.audit.persistence.AlertEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlertEventListener {

    private final AlertEventRepository alertEventRepository;

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
        } catch (Exception e) {
            log.error("Failed to create alert event: severity={}, title={}, error={}",
                    event.severity(), event.title(), e.getMessage(), e);
        }
    }
}
