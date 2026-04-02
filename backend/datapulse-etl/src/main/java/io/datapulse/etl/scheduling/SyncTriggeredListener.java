package io.datapulse.etl.scheduling;

import java.util.HashMap;
import java.util.Map;

import io.datapulse.etl.domain.IngestResultReporter;
import io.datapulse.etl.persistence.JobExecutionRepository;
import io.datapulse.integration.domain.event.SyncTriggeredEvent;
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
public class SyncTriggeredListener {

  private final JobExecutionRepository jobExecutionRepository;
  private final OutboxService outboxService;
  private final IngestResultReporter resultReporter;

  @EventListener
  @Transactional
  public void onSyncTriggered(SyncTriggeredEvent event) {
    Long connectionId = event.connectionId();

    if (jobExecutionRepository.existsActiveForConnection(connectionId)) {
      log.info("Active job already exists, skipping manual sync: connectionId={}",
          connectionId);
      return;
    }

    long jobId = jobExecutionRepository.insert(connectionId, "MANUAL_SYNC");

    Map<String, Object> payload = new HashMap<>();
    payload.put("jobExecutionId", jobId);
    payload.put("connectionId", connectionId);
    if (event.domains() != null && !event.domains().isEmpty()) {
      payload.put("domains", event.domains());
    }

    outboxService.createEvent(
        OutboxEventType.ETL_SYNC_EXECUTE,
        "job_execution",
        jobId,
        payload);

    resultReporter.updateSyncStateSyncing(connectionId);

    log.info("MANUAL_SYNC dispatched: connectionId={}, jobExecutionId={}, domains={}",
        connectionId, jobId, event.domains());
  }
}
