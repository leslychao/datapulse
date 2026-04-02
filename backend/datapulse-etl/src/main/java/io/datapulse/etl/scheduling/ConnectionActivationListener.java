package io.datapulse.etl.scheduling;

import java.util.Map;

import io.datapulse.etl.domain.IngestResultReporter;
import io.datapulse.etl.persistence.JobExecutionRepository;
import io.datapulse.integration.domain.ConnectionStatus;
import io.datapulse.integration.domain.event.ConnectionStatusChangedEvent;
import io.datapulse.platform.outbox.OutboxEventType;
import io.datapulse.platform.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConnectionActivationListener {

  private final JobExecutionRepository jobExecutionRepository;
  private final OutboxService outboxService;
  private final IngestResultReporter resultReporter;

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
}
