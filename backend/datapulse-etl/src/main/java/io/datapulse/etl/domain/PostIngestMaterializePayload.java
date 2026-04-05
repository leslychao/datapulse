package io.datapulse.etl.domain;

import com.fasterxml.jackson.databind.JsonNode;
import io.datapulse.etl.persistence.JobExecutionRow;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON payload for {@link io.datapulse.platform.outbox.OutboxEventType#ETL_POST_INGEST_MATERIALIZE}.
 */
public record PostIngestMaterializePayload(
    long jobExecutionId,
    long workspaceId,
    long connectionId,
    String syncScope,
    String ingestStatus,
    List<String> completedDomains,
    List<String> failedDomains,
    boolean promoSyncCompleted) {

  public static PostIngestMaterializePayload from(
      JobExecutionRow job,
      long workspaceId,
      JobExecutionStatus ingestStatus,
      Map<EtlEventType, EventResult> results) {
    List<String> completedDomains =
        results.entrySet().stream()
            .filter(e -> e.getValue().isSuccess())
            .map(e -> e.getKey().name())
            .toList();
    List<String> failedDomains =
        results.entrySet().stream()
            .filter(e -> e.getValue().isFailed())
            .map(e -> e.getKey().name())
            .toList();
    EventResult promoResult = results.get(EtlEventType.PROMO_SYNC);
    boolean promoSyncCompleted = promoResult != null && promoResult.isSuccess();
    return new PostIngestMaterializePayload(
        job.getId(),
        workspaceId,
        job.getConnectionId(),
        job.getEventType(),
        ingestStatus.name(),
        completedDomains,
        failedDomains,
        promoSyncCompleted);
  }

  public Map<String, Object> toOutboxPayload() {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("jobExecutionId", jobExecutionId);
    payload.put("workspaceId", workspaceId);
    payload.put("connectionId", connectionId);
    payload.put("syncScope", syncScope);
    payload.put("ingestStatus", ingestStatus);
    payload.put("completedDomains", new ArrayList<>(completedDomains));
    payload.put("failedDomains", new ArrayList<>(failedDomains));
    payload.put("promoSyncCompleted", promoSyncCompleted);
    return payload;
  }

  public static PostIngestMaterializePayload fromJson(JsonNode n) {
    long jobExecutionId = n.path("jobExecutionId").asLong(0);
    long workspaceId = n.path("workspaceId").asLong(0);
    long connectionId = n.path("connectionId").asLong(0);
    String syncScope = textOrNull(n, "syncScope");
    String ingestStatus = textOrNull(n, "ingestStatus");
    boolean promoSyncCompleted = n.path("promoSyncCompleted").asBoolean(false);
    return new PostIngestMaterializePayload(
        jobExecutionId,
        workspaceId,
        connectionId,
        syncScope,
        ingestStatus,
        readStringList(n, "completedDomains"),
        readStringList(n, "failedDomains"),
        promoSyncCompleted);
  }

  private static String textOrNull(JsonNode n, String field) {
    JsonNode v = n.get(field);
    if (v == null || v.isNull() || v.asText().isEmpty()) {
      return null;
    }
    return v.asText();
  }

  private static List<String> readStringList(JsonNode n, String field) {
    JsonNode arr = n.get(field);
    if (arr == null || !arr.isArray()) {
      return List.of();
    }
    List<String> out = new ArrayList<>();
    arr.forEach(node -> out.add(node.asText()));
    return List.copyOf(out);
  }
}
