package io.datapulse.api.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.api.config.RabbitTopologyConfig;
import io.datapulse.etl.domain.IngestOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumes ETL_SYNC_EXECUTE and ETL_SYNC_RETRY messages from RabbitMQ.
 *
 * <p>Both event types end up in the same {@code etl.sync} queue:
 * <ul>
 *   <li>ETL_SYNC_EXECUTE — published directly from outbox (manual or scheduled sync)</li>
 *   <li>ETL_SYNC_RETRY — published to {@code etl.sync.wait} with TTL, then DLX-forwarded here</li>
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
  private final ObjectMapper objectMapper;

  @RabbitListener(queues = RabbitTopologyConfig.ETL_SYNC_QUEUE)
  public void onMessage(Message message) {
    try {
      JsonNode payload = objectMapper.readTree(message.getBody());
      long jobExecutionId = payload.path("jobExecutionId").asLong();

      if (jobExecutionId <= 0) {
        log.error("Invalid jobExecutionId in ETL sync message: payload={}",
            new String(message.getBody()));
        return;
      }

      String eventType = extractHeader(message, "x-event-type");
      log.info("Processing ETL sync: jobExecutionId={}, eventType={}", jobExecutionId, eventType);

      ingestOrchestrator.processSync(jobExecutionId);
    } catch (Exception e) {
      log.error("Poison pill detected in etl.sync queue: messageId={}, error={}",
          message.getMessageProperties().getMessageId(), e.getMessage(), e);
    }
  }

  private String extractHeader(Message message, String headerName) {
    Object value = message.getMessageProperties().getHeader(headerName);
    return value != null ? value.toString() : null;
  }
}
