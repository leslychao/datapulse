package io.datapulse.audit.domain;

import io.datapulse.audit.domain.event.AuditEvent;
import io.datapulse.audit.persistence.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuditEventListener {

    private final AuditLogRepository auditLogRepository;

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAuditEvent(AuditEvent event) {
        try {
            auditLogRepository.insert(
                    event.workspaceId(),
                    event.actorType(),
                    event.actorUserId(),
                    event.actionType(),
                    event.entityType(),
                    event.entityId(),
                    event.outcome(),
                    event.details(),
                    event.ipAddress(),
                    event.correlationId()
            );
            log.debug("Audit log written: actionType={}, entityType={}, entityId={}",
                    event.actionType(), event.entityType(), event.entityId());
        } catch (Exception e) {
            log.error("Failed to write audit log: actionType={}, entityType={}, entityId={}, error={}",
                    event.actionType(), event.entityType(), event.entityId(), e.getMessage(), e);
        }
    }
}
