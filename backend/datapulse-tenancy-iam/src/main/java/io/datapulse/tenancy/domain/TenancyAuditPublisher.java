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
        Long workspaceId = workspaceContext.getWorkspaceId();
        if (workspaceId == null) {
            workspaceId = 0L;
        }

        eventPublisher.publishEvent(new AuditEvent(
                workspaceId,
                "USER",
                workspaceContext.getUserId(),
                actionType,
                entityType,
                entityId,
                "SUCCESS",
                null,
                null,
                null
        ));
    }

    public void publish(String actionType, String entityType, String entityId, String details) {
        Long workspaceId = workspaceContext.getWorkspaceId();
        if (workspaceId == null) {
            workspaceId = 0L;
        }

        eventPublisher.publishEvent(new AuditEvent(
                workspaceId,
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
