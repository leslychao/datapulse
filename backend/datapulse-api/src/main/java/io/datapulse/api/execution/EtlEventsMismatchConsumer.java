package io.datapulse.api.execution;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.api.config.RabbitTopologyConfig;
import io.datapulse.sellerops.domain.MismatchMonitorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class EtlEventsMismatchConsumer {

  private final MismatchMonitorService mismatchMonitorService;
  private final EtlSyncCompletedPayloadResolver etlSyncCompletedPayloadResolver;
  private final ObjectMapper objectMapper;

  @RabbitListener(queues = RabbitTopologyConfig.ETL_EVENTS_MISMATCH_QUEUE)
  public void onEtlEvent(Message message) {
    try {
      String eventType = extractHeader(message, "x-event-type");
      if (!"ETL_SYNC_COMPLETED".equals(eventType)) {
        return;
      }

      Map<String, Object> payload = objectMapper.readValue(
          message.getBody(), new TypeReference<>() {});

      Long workspaceId =
          etlSyncCompletedPayloadResolver.resolveWorkspaceId(payload).orElse(null);
      if (workspaceId == null) {
        log.warn(
            "ETL_SYNC_COMPLETED missing workspaceId (and could not resolve from connectionId),"
                + " skipping mismatch check");
        return;
      }

      log.info("ETL_SYNC_COMPLETED received, triggering mismatch check: workspaceId={}",
          workspaceId);
      mismatchMonitorService.checkAllMismatches(workspaceId);
    } catch (Exception e) {
      log.error("Error processing ETL event for mismatch check: messageId={}, error={}",
          message.getMessageProperties().getMessageId(), e.getMessage(), e);
    }
  }

  private String extractHeader(Message message, String headerName) {
    Object value = message.getMessageProperties().getHeader(headerName);
    return value != null ? value.toString() : null;
  }

}
