package io.datapulse.etl.scheduling;

import java.util.Map;

import io.datapulse.etl.domain.ConnectionStaleJobReconciler;
import io.datapulse.etl.domain.IngestResultReporter;
import io.datapulse.etl.persistence.JobExecutionRepository;
import io.datapulse.etl.persistence.canonical.CanonicalDataReassignmentRepository;
import io.datapulse.integration.domain.ConnectionStatus;
import io.datapulse.integration.domain.event.ConnectionStatusChangedEvent;
import io.datapulse.integration.persistence.MarketplaceConnectionEntity;
import io.datapulse.integration.persistence.MarketplaceConnectionRepository;
import io.datapulse.platform.outbox.OutboxEventType;
import io.datapulse.platform.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Same transactional coupling as {@link SyncTriggeredListener}: {@link EventListener} +
 * {@link Transactional}({@code REQUIRED}) participates in the publisher transaction so
 * {@code job_execution} + outbox + sync state updates are atomic with connection activation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConnectionActivationListener {

  private final JobExecutionRepository jobExecutionRepository;
  private final ConnectionStaleJobReconciler connectionStaleJobReconciler;
  private final OutboxService outboxService;
  private final IngestResultReporter resultReporter;
  private final MarketplaceConnectionRepository connectionRepository;
  private final CanonicalDataReassignmentRepository reassignmentRepository;

  @EventListener
  @Transactional
  public void onConnectionActivated(ConnectionStatusChangedEvent event) {
    if (!ConnectionStatus.ACTIVE.name().equals(event.newStatus())) {
      return;
    }
    if (!ConnectionStatus.PENDING_VALIDATION.name().equals(event.oldStatus())) {
      return;
    }

    Long connectionId = event.connectionId();

    reassignFromArchivedPredecessor(connectionId);

    connectionStaleJobReconciler.reconcileForDispatch(connectionId);
    if (jobExecutionRepository.existsActiveForConnection(connectionId)) {
      log.info("Active job already exists for activated connection, skipping FULL_SYNC: connectionId={}",
          connectionId);
      return;
    }

    long jobId = jobExecutionRepository.insert(connectionId, "FULL_SYNC");

    outboxService.createEvent(
        OutboxEventType.ETL_SYNC_EXECUTE,
        "job_execution",
        jobId,
        Map.of("jobExecutionId", jobId, "connectionId", connectionId));

    resultReporter.updateSyncStateSyncing(connectionId);

    log.info("FULL_SYNC dispatched for activated connection: connectionId={}, jobExecutionId={}",
        connectionId, jobId);
  }

  private void reassignFromArchivedPredecessor(Long newConnectionId) {
    MarketplaceConnectionEntity connection = connectionRepository.findById(newConnectionId)
        .orElse(null);
    if (connection == null || connection.getExternalAccountId() == null) {
      return;
    }

    connectionRepository
        .findFirstByWorkspaceIdAndMarketplaceTypeAndExternalAccountIdAndStatusAndIdNot(
            connection.getWorkspaceId(),
            connection.getMarketplaceType(),
            connection.getExternalAccountId(),
            ConnectionStatus.ARCHIVED.name(),
            newConnectionId)
        .ifPresent(archived -> {
          int rows = reassignmentRepository.reassign(archived.getId(), newConnectionId);
          log.info("Canonical data reassigned from archived predecessor: "
                  + "archivedConnectionId={}, newConnectionId={}, totalRows={}",
              archived.getId(), newConnectionId, rows);
        });
  }
}
