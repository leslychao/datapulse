package io.datapulse.platform.audit;

import io.datapulse.platform.security.WorkspaceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Centralized audit event publisher.
 * Resolves workspace context automatically when available.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditPublisher {

  private final ApplicationEventPublisher eventPublisher;
  private final WorkspaceContext workspaceContext;

  public void publish(String actionType, String entityType, String entityId) {
    publish(actionType, entityType, entityId, "SUCCESS", null);
  }

  public void publish(String actionType, String entityType, String entityId,
      String details) {
    publish(actionType, entityType, entityId, "SUCCESS", details);
  }

  public void publish(String actionType, String entityType, String entityId,
      String outcome, String details) {
    Long wsId = null;
    Long userId = null;
    try {
      wsId = workspaceContext.getWorkspaceId();
      userId = workspaceContext.getUserId();
    } catch (Exception ignored) {
    }
    doPublish(wsId, "USER", userId, actionType, entityType, entityId,
        outcome, details);
  }

  public void publishSystem(String actionType, String entityType,
      String entityId) {
    doPublish(null, "SYSTEM", null, actionType, entityType, entityId,
        "SUCCESS", null);
  }

  public void publishSystem(Long actorUserId, String actionType,
      String entityType, String entityId) {
    doPublish(null, "SYSTEM", actorUserId, actionType, entityType, entityId,
        "SUCCESS", null);
  }

  public void publishSystemWithWorkspace(Long workspaceId, String actionType,
      String entityType, String entityId, String details) {
    doPublish(workspaceId, "SYSTEM", null, actionType, entityType, entityId,
        "SUCCESS", details);
  }

  private void doPublish(Long wsId, String actorType, Long actorUserId,
      String actionType, String entityType, String entityId,
      String outcome, String details) {
    try {
      eventPublisher.publishEvent(new AuditEvent(
          wsId, actorType, actorUserId, actionType,
          entityType, entityId, outcome, details, null, null));
    } catch (Exception e) {
      log.error("Failed to publish audit event: actionType={}, entityType={}, "
          + "entityId={}, error={}", actionType, entityType, entityId,
          e.getMessage());
    }
  }
}
