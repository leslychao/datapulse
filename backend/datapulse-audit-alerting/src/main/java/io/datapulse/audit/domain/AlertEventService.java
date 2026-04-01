package io.datapulse.audit.domain;

import java.util.List;

import io.datapulse.audit.api.AlertEventFilter;
import io.datapulse.audit.api.AlertEventResponse;
import io.datapulse.audit.api.AlertSummaryResponse;
import io.datapulse.audit.domain.event.AlertEventCreatedEvent;
import io.datapulse.audit.domain.event.AlertResolvedEvent;
import io.datapulse.audit.persistence.AlertEventRepository;
import io.datapulse.common.error.MessageCodes;
import io.datapulse.common.exception.BadRequestException;
import io.datapulse.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertEventService {

    private final AlertEventRepository alertEventRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public AlertEventResponse getAlert(long id, long workspaceId) {
        return alertEventRepository.findById(id, workspaceId)
                .orElseThrow(() -> NotFoundException.of(MessageCodes.ALERT_EVENT_NOT_FOUND, id));
    }

    @Transactional(readOnly = true)
    public AlertSummaryResponse getSummary(long workspaceId) {
        return alertEventRepository.getSummary(workspaceId);
    }

    @Transactional(readOnly = true)
    public Page<AlertEventResponse> listAlerts(long workspaceId, AlertEventFilter filter, Pageable pageable) {
        String sortColumn = pageable.getSort().isSorted()
                ? pageable.getSort().iterator().next().getProperty()
                : "openedAt";

        List<AlertEventResponse> content = alertEventRepository.findAll(
                workspaceId, filter, sortColumn,
                pageable.getPageSize(), pageable.getOffset());
        long total = alertEventRepository.count(workspaceId, filter);

        return new PageImpl<>(content, pageable, total);
    }

    @Transactional
    public AlertEventResponse acknowledge(long id, long workspaceId, long userId) {
        int updated = alertEventRepository.acknowledge(id, workspaceId, userId);
        if (updated == 0) {
            alertEventRepository.findById(id, workspaceId)
                    .orElseThrow(() -> NotFoundException.of(MessageCodes.ALERT_EVENT_NOT_FOUND, id));
            throw BadRequestException.of(MessageCodes.ALERT_EVENT_INVALID_STATE, id, "OPEN → ACKNOWLEDGED");
        }
        return alertEventRepository.findById(id, workspaceId).orElseThrow();
    }

    @Transactional
    public AlertEventResponse resolve(long id, long workspaceId) {
        AlertEventResponse before = alertEventRepository.findById(id, workspaceId)
                .orElseThrow(() -> NotFoundException.of(MessageCodes.ALERT_EVENT_NOT_FOUND, id));

        int updated = alertEventRepository.resolve(id, workspaceId);
        if (updated == 0) {
            throw BadRequestException.of(MessageCodes.ALERT_EVENT_INVALID_STATE, id,
                    "ACKNOWLEDGED → RESOLVED");
        }

        eventPublisher.publishEvent(new AlertResolvedEvent(
                id, workspaceId, before.connectionId(),
                before.severity(), before.title(), "MANUAL"));

        return alertEventRepository.findById(id, workspaceId).orElseThrow();
    }

    /**
     * Creates a rule-based alert event and publishes {@link AlertEventCreatedEvent}.
     * Called by scheduled checkers.
     */
    @Transactional
    public long createRuleBasedAlert(long alertRuleId, long workspaceId, Long connectionId,
                                     String ruleType, String severity, String title,
                                     String details, boolean blocksAutomation) {
        long alertEventId = alertEventRepository.insertRuleBased(
                alertRuleId, workspaceId, connectionId,
                severity, title, details, blocksAutomation);

        eventPublisher.publishEvent(new AlertEventCreatedEvent(
                alertEventId, workspaceId, connectionId,
                ruleType, severity, title, "OPEN", blocksAutomation));

        log.info("Rule-based alert created: alertEventId={}, ruleType={}, severity={}, connectionId={}",
                alertEventId, ruleType, severity, connectionId);

        return alertEventId;
    }

    /**
     * Auto-resolves active alerts for a given rule + connection and publishes events.
     */
    @Transactional
    public int autoResolve(long alertRuleId, long connectionId, long workspaceId) {
        List<AlertEventResponse> active = alertEventRepository
                .findActiveByRuleAndConnection(alertRuleId, connectionId);
        if (active.isEmpty()) {
            return 0;
        }

        int resolved = alertEventRepository.autoResolve(alertRuleId, connectionId);

        for (AlertEventResponse event : active) {
            eventPublisher.publishEvent(new AlertResolvedEvent(
                    event.id(), workspaceId, connectionId,
                    event.severity(), event.title(), "AUTO"));
        }

        log.info("Auto-resolved alerts: count={}, alertRuleId={}, connectionId={}",
                resolved, alertRuleId, connectionId);

        return resolved;
    }

    @Transactional(readOnly = true)
    public boolean existsBlockingAlert(long workspaceId, long connectionId) {
        return alertEventRepository.existsBlockingAlert(workspaceId, connectionId);
    }
}
