package io.datapulse.audit.domain;

import io.datapulse.platform.audit.AutomationBlockerChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AlertAutomationBlockerChecker implements AutomationBlockerChecker {

    private final AlertEventService alertEventService;

    @Override
    public boolean isBlocked(long workspaceId, long connectionId) {
        return alertEventService.existsBlockingAlert(workspaceId, connectionId);
    }
}
