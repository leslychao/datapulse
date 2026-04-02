package io.datapulse.api.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.analytics.domain.MaterializationService;
import io.datapulse.api.config.RabbitTopologyConfig;
import io.datapulse.etl.domain.IngestOrchestrator;
import io.datapulse.platform.outbox.OutboxEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumes ETL_SYNC_EXECUTE, ETL_SYNC_RETRY and REMATERIALIZATION_REQUESTED
 * messages from RabbitMQ.
 *
 * <p>All event types end up in the same {@code etl.sync} queue:
 * <ul>
 *   <li>ETL_SYNC_EXECUTE — published directly from outbox (manual or scheduled sync)</li>
 *   <li>ETL_SYNC_RETRY — published to {@code etl.sync.wait} with TTL, then DLX-forwarded here</li>
 *   <li>REMATERIALIZATION_REQUESTED — triggers ClickHouse re-materialization</li>
 * </ul>
 *
 * <p>Error handling: unhandled exceptions are caught and logged (poison pill pattern).
 * Business-level retries are managed by {@link IngestOrchestrator} via outbox.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EtlSyncConsumer {

  private final IngestOrchestrator ingestOrchestrator;
  private final MaterializationService materializationService;
  private final ObjectMapper objectMapper;

  @RabbitListener(queues = RabbitTopologyConfig.ETL_SYNC_QUEUE)
  public void onMessage(Message message) {
    try {
      String eventType = extractHeader(message, "x-event-type");

      if (OutboxEventType.REMATERIALIZATION_REQUESTED.name().equals(eventType)) {
        handleRematerialization(message);
        return;
      }

      JsonNode payload = objectMapper.readTree(message.getBody());
      long jobExecutionId = payload.path("jobExecutionId").asLong();

      if (jobExecutionId <= 0) {
        log.error("Invalid jobExecutionId in ETL sync message: payload={}",
            new String(message.getBody()));
        return;
      }

      log.info("Processing ETL sync: jobExecutionId={}, eventType={}", jobExecutionId, eventType);
      ingestOrchestrator.processSync(jobExecutionId);
    } catch (Exception e) {
      log.error("Poison pill detected in etl.sync queue: messageId={}, error={}",
          message.getMessageProperties().getMessageId(), e.getMessage(), e);
    }
  }

  private void handleRematerialization(Message message) {
    try {
      JsonNode payload = objectMapper.readTree(message.getBody());
      String scope = payload.path("scope").asText("FULL");
      String reason = payload.path("reason").asText("unknown");
      log.info("Processing rematerialization request: scope={}, reason={}", scope, reason);

      materializationService.runFullRematerialization();

      log.info("Rematerialization completed: scope={}, reason={}", scope, reason);
    } catch (Exception e) {
      log.error("Rematerialization failed: messageId={}", 
          message.getMessageProperties().getMessageId(), e);
    }
  }

  private String extractHeader(Message message, String headerName) {
    Object value = message.getMessageProperties().getHeader(headerName);
    return value != null ? value.toString() : null;
  }
}
