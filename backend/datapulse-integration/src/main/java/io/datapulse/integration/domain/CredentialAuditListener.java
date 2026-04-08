package io.datapulse.integration.domain;

import io.datapulse.integration.domain.event.CredentialAccessedEvent;
import io.datapulse.integration.domain.event.CredentialRotatedEvent;
import io.datapulse.platform.audit.AuditPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Writes audit_log entries for credential operations:
 * <ul>
 *   <li>{@code credential.rotate} — user-initiated rotation via ConnectionService</li>
 *   <li>{@code credential.access} — system reads (ETL sync, health check, execution, promo)</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CredentialAuditListener {

  private static final String ENTITY_TYPE = "marketplace_connection";

  private final AuditPublisher auditPublisher;

  @Async("notificationExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onCredentialRotated(CredentialRotatedEvent event) {
    try {
      auditPublisher.publish(
          "credential.rotate",
          ENTITY_TYPE,
          String.valueOf(event.connectionId()));
    } catch (Exception e) {
      log.error("Failed to audit credential rotation: connectionId={}, error={}",
          event.connectionId(), e.getMessage(), e);
    }
  }

  @Async("notificationExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onCredentialAccessed(CredentialAccessedEvent event) {
    try {
      auditPublisher.publishSystemWithWorkspace(
          event.workspaceId(),
          "credential.access",
          ENTITY_TYPE,
          String.valueOf(event.connectionId()),
          event.purpose());
    } catch (Exception e) {
      log.error("Failed to audit credential access: connectionId={}, purpose={}, error={}",
          event.connectionId(), event.purpose(), e.getMessage(), e);
    }
  }
}
