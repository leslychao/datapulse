package io.datapulse.tenancy.domain;

import io.datapulse.platform.audit.AuditEvent;
import io.datapulse.platform.security.WorkspaceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TenancyAuditPublisher {

    private final ApplicationEventPublisher eventPublisher;
    private final WorkspaceContext workspaceContext;

    public void publish(String actionType, String entityType, String entityId) {
        publish(actionType, entityType, entityId, null);
    }

    public void publish(String actionType, String entityType, String entityId, String details) {
        eventPublisher.publishEvent(new AuditEvent(
                workspaceContext.getWorkspaceId(),
                "USER",
                workspaceContext.getUserId(),
                actionType,
                entityType,
                entityId,
                "SUCCESS",
                details,
                null,
                null
        ));
    }
}
