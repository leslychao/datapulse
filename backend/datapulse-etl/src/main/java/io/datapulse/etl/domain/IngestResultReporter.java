package io.datapulse.etl.domain;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.datapulse.platform.etl.PostIngestMaterializationResult;
import io.datapulse.etl.persistence.JobExecutionRow;
import io.datapulse.integration.domain.SyncStatus;
import io.datapulse.integration.persistence.MarketplaceSyncStateEntity;
import io.datapulse.integration.persistence.MarketplaceSyncStateRepository;
import io.datapulse.platform.outbox.OutboxEventType;
import io.datapulse.platform.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class IngestResultReporter {

    private final OutboxService outboxService;
    private final MarketplaceSyncStateRepository syncStateRepository;
    private final ObjectMapper objectMapper;

    public void updateSyncStateSyncing(long connectionId) {
        List<MarketplaceSyncStateEntity> states =
                syncStateRepository.findAllByMarketplaceConnectionId(connectionId);
        OffsetDateTime now = OffsetDateTime.now();
        for (MarketplaceSyncStateEntity state : states) {
            state.setStatus(SyncStatus.SYNCING.name());
            state.setLastSyncAt(now);
            state.setErrorMessage(null);
        }
        syncStateRepository.saveAll(states);
    }

    public void updateSyncStateSuccess(long connectionId) {
        List<MarketplaceSyncStateEntity> states =
                syncStateRepository.findAllByMarketplaceConnectionId(connectionId);
        OffsetDateTime now = OffsetDateTime.now();
        for (MarketplaceSyncStateEntity state : states) {
            state.setStatus(SyncStatus.IDLE.name());
            state.setLastSuccessAt(now);
            state.setNextScheduledAt(now.plusHours(6));
            state.setErrorMessage(null);
        }
        syncStateRepository.saveAll(states);
    }

    public void updateSyncStateError(long connectionId, String errorMessage) {
        List<MarketplaceSyncStateEntity> states =
                syncStateRepository.findAllByMarketplaceConnectionId(connectionId);
        for (MarketplaceSyncStateEntity state : states) {
            state.setStatus(SyncStatus.ERROR.name());
            if (errorMessage != null) {
                state.setErrorMessage(
                        errorMessage.length() > 1000 ? errorMessage.substring(0, 1000) : errorMessage);
            }
        }
        syncStateRepository.saveAll(states);
    }

    public void publishCompletionEvent(JobExecutionRow job, Map<EtlEventType, EventResult> results) {
        List<String> completedDomains = results.entrySet().stream()
                .filter(e -> e.getValue().isSuccess())
                .map(e -> e.getKey().name())
                .toList();
        List<String> failedDomains = results.entrySet().stream()
                .filter(e -> e.getValue().isFailed())
                .map(e -> e.getKey().name())
                .toList();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("connectionId", job.getConnectionId());
        payload.put("jobExecutionId", job.getId());
        payload.put("syncScope", job.getEventType());
        payload.put("completedDomains", completedDomains);
        payload.put("failedDomains", failedDomains);

        outboxService.createEvent(
                OutboxEventType.ETL_SYNC_COMPLETED,
                "job_execution",
                job.getId(),
                payload);
    }

    public String buildErrorDetails(Map<EtlEventType, EventResult> results) {
        Map<String, Object> details = new LinkedHashMap<>();

        List<String> failedDomains = results.entrySet().stream()
                .filter(e -> e.getValue().isFailed())
                .map(e -> e.getKey().name())
                .toList();
        List<String> completedDomains = results.entrySet().stream()
                .filter(e -> e.getValue().isSuccess())
                .map(e -> e.getKey().name())
                .toList();

        details.put("failed_domains", failedDomains);
        details.put("completed_domains", completedDomains);

        List<Map<String, Object>> errors = new ArrayList<>();
        for (Map.Entry<EtlEventType, EventResult> entry : results.entrySet()) {
            EventResult result = entry.getValue();
            if (result.isFailed() || result.status() == EventResultStatus.COMPLETED_WITH_ERRORS) {
                for (SubSourceResult ssr : result.subSourceResults()) {
                    if (!ssr.errors().isEmpty()) {
                        Map<String, Object> errorEntry = new LinkedHashMap<>();
                        errorEntry.put("domain", entry.getKey().name());
                        errorEntry.put("event", entry.getKey().name());
                        errorEntry.put("error_type", "API_ERROR");
                        errorEntry.put("message", ssr.errors().get(0));
                        errorEntry.put("records_processed", ssr.recordsProcessed());
                        errorEntry.put("records_skipped", ssr.recordsSkipped());
                        errors.add(errorEntry);
                    }
                }
            }
        }
        details.put("errors", errors);

        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    /**
     * Merges materialization failures into ingest {@code error_details} JSON. No-op when
     * materialization fully succeeded.
     */
    public String mergeMaterializationIntoErrorDetails(
            String ingestErrorDetailsJson, PostIngestMaterializationResult materialization) {
        if (materialization.fullySucceeded()) {
            return ingestErrorDetailsJson;
        }
        try {
            ObjectNode root;
            if (ingestErrorDetailsJson == null || ingestErrorDetailsJson.isBlank()) {
                root = objectMapper.createObjectNode();
            } else {
                root = (ObjectNode) objectMapper.readTree(ingestErrorDetailsJson);
            }
            ObjectNode mat = objectMapper.createObjectNode();
            mat.put("fully_succeeded", false);
            if (materialization.fatalError() != null) {
                mat.put("fatal_error", materialization.fatalError());
            }
            ArrayNode failed = objectMapper.createArrayNode();
            for (String table : materialization.failedTables()) {
                failed.add(table);
            }
            mat.set("failed_tables", failed);
            root.set("materialization", mat);
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            log.warn("Failed to merge materialization into error_details: {}", e.getMessage());
            return ingestErrorDetailsJson;
        }
    }
}
