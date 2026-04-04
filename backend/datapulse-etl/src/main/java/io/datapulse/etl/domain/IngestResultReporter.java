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

import io.datapulse.etl.config.IngestProperties;
import io.datapulse.etl.persistence.JobExecutionRow;
import io.datapulse.integration.api.SyncStatusPushReason;
import io.datapulse.integration.domain.SyncStatus;
import io.datapulse.integration.domain.event.ConnectionSyncHealthInvalidatedEvent;
import io.datapulse.integration.persistence.MarketplaceSyncStateEntity;
import io.datapulse.integration.persistence.MarketplaceSyncStateRepository;
import io.datapulse.platform.etl.PostIngestMaterializationResult;
import io.datapulse.platform.outbox.OutboxEventType;
import io.datapulse.platform.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class IngestResultReporter {

    private final OutboxService outboxService;
    private final MarketplaceSyncStateRepository syncStateRepository;
    private final ObjectMapper objectMapper;
    private final IngestProperties ingestProperties;
    private final ApplicationEventPublisher eventPublisher;

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
        publishSyncHealthInvalidated(connectionId, SyncStatusPushReason.STATE_CHANGED);
    }

    /**
     * Terminal ingest success only: persists IDLE sync state, inserts {@code ETL_SYNC_COMPLETED} outbox row,
     * then publishes {@link SyncStatusPushReason#ETL_JOB_COMPLETED}. Keeps UI invalidation aligned with
     * completion fan-out; do not call without the outbox write.
     */
    public void recordSuccessfulTerminalSync(
            JobExecutionRow job, long workspaceId, Map<EtlEventType, EventResult> results) {
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
        recordSuccessfulTerminalSyncLists(job, workspaceId, completedDomains, failedDomains);
    }

    /**
     * Same as {@link #recordSuccessfulTerminalSync} but uses precomputed domain lists (e.g. async
     * materialization handler that no longer has the full {@link EventResult} map).
     */
    public void recordSuccessfulTerminalSyncLists(
            JobExecutionRow job,
            long workspaceId,
            List<String> completedDomains,
            List<String> failedDomains) {
        applySuccessfulSyncState(job.getConnectionId());
        writeCompletionOutboxLists(job, workspaceId, completedDomains, failedDomains);
        publishSyncHealthInvalidated(job.getConnectionId(), SyncStatusPushReason.ETL_JOB_COMPLETED);
    }

    private void applySuccessfulSyncState(long connectionId) {
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
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime nextAttempt = now.plus(ingestProperties.syncNextAttemptAfterError());
        for (MarketplaceSyncStateEntity state : states) {
            state.setStatus(SyncStatus.ERROR.name());
            state.setNextScheduledAt(nextAttempt);
            if (errorMessage != null) {
                state.setErrorMessage(
                        errorMessage.length() > 1000 ? errorMessage.substring(0, 1000) : errorMessage);
            }
        }
        syncStateRepository.saveAll(states);
        publishSyncHealthInvalidated(connectionId, SyncStatusPushReason.STATE_CHANGED);
    }

    private void writeCompletionOutboxLists(
            JobExecutionRow job,
            long workspaceId,
            List<String> completedDomains,
            List<String> failedDomains) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workspaceId", workspaceId);
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

    private void publishSyncHealthInvalidated(long connectionId, SyncStatusPushReason reason) {
        eventPublisher.publishEvent(new ConnectionSyncHealthInvalidatedEvent(connectionId, reason));
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
