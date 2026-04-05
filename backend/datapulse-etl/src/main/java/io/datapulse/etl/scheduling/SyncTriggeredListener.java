package io.datapulse.etl.scheduling;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashMap;
import java.util.Map;

import io.datapulse.etl.domain.ConnectionStaleJobReconciler;
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

/**
 * Dispatches manual sync jobs in the <strong>same transaction</strong> as the publisher of
 * {@link io.datapulse.integration.domain.event.SyncTriggeredEvent} (typically
 * {@code ConnectionService}). {@link EventListener} runs synchronously in the publisher thread
 * before commit; {@link Transactional} with default {@code REQUIRED} joins that transaction so job
 * insert + outbox row commit or roll back with the triggering command. Do not switch to
 * {@code @TransactionalEventListener(AFTER_COMMIT)} without re-auditing atomicity with the
 * connection/workspace flow.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SyncTriggeredListener {

  private final JobExecutionRepository jobExecutionRepository;
  private final ConnectionStaleJobReconciler connectionStaleJobReconciler;
  private final OutboxService outboxService;
  private final IngestResultReporter resultReporter;
  private final ObjectMapper objectMapper;

  @EventListener
  @Transactional
  public void onSyncTriggered(SyncTriggeredEvent event) {
    Long connectionId = event.connectionId();

    connectionStaleJobReconciler.reconcileForDispatch(connectionId);
    if (jobExecutionRepository.existsActiveForConnection(connectionId)) {
      log.info("Active job already exists, skipping manual sync: connectionId={}",
          connectionId);
      return;
    }

    String paramsJson = buildParamsJson(event);
    long jobId = jobExecutionRepository.insert(connectionId, "MANUAL_SYNC", paramsJson);

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

  private String buildParamsJson(SyncTriggeredEvent event) {
    if (event.domains() == null || event.domains().isEmpty()) {
      return null;
    }
    try {
      ObjectNode root = objectMapper.createObjectNode();
      ArrayNode arr = objectMapper.createArrayNode();
      for (String d : event.domains()) {
        arr.add(d);
      }
      root.set("domains", arr);
      return objectMapper.writeValueAsString(root);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize job params for MANUAL_SYNC", e);
    }
  }
}
