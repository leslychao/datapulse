package io.datapulse.etl.domain;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.datapulse.common.exception.BadRequestException;
import io.datapulse.common.exception.ConflictException;
import io.datapulse.common.exception.NotFoundException;
import io.datapulse.common.error.MessageCodes;
import io.datapulse.etl.api.JobExecutionResponse;
import io.datapulse.etl.api.JobFilter;
import io.datapulse.etl.api.JobItemResponse;
import io.datapulse.etl.api.JobRetryResponse;
import io.datapulse.etl.persistence.JobExecutionRepository;
import io.datapulse.etl.persistence.JobExecutionRow;
import io.datapulse.etl.persistence.JobItemRepository;
import io.datapulse.etl.persistence.JobItemRow;
import io.datapulse.integration.persistence.MarketplaceConnectionRepository;
import io.datapulse.platform.outbox.OutboxEventType;
import io.datapulse.platform.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobMonitoringService {

    private static final Set<JobExecutionStatus> RETRYABLE_STATUSES = EnumSet.of(
            JobExecutionStatus.FAILED, JobExecutionStatus.COMPLETED_WITH_ERRORS);

    private static final String INCREMENTAL_EVENT_TYPE = "INCREMENTAL";

    private final JobExecutionRepository jobExecutionRepository;
    private final JobItemRepository jobItemRepository;
    private final MarketplaceConnectionRepository connectionRepository;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Page<JobExecutionResponse> listJobs(long connectionId, long workspaceId,
                                               JobFilter filter, Pageable pageable) {
        ensureConnectionBelongsToWorkspace(connectionId, workspaceId);

        List<JobExecutionRow> rows = jobExecutionRepository.findByConnectionId(
                connectionId, filter.status(), filter.from(), filter.to(),
                pageable.getPageSize(), pageable.getOffset());

        long total = jobExecutionRepository.countByConnectionId(
                connectionId, filter.status(), filter.from(), filter.to());

        List<JobExecutionResponse> content = rows.stream().map(this::toResponse).toList();
        return new PageImpl<>(content, pageable, total);
    }

    @Transactional(readOnly = true)
    public JobExecutionResponse getJob(long jobId, long workspaceId) {
        JobExecutionRow job = findJobOrThrow(jobId);
        ensureConnectionBelongsToWorkspace(job.getConnectionId(), workspaceId);
        return toResponse(job);
    }

    @Transactional(readOnly = true)
    public List<JobItemResponse> getJobItems(long jobId, long workspaceId) {
        JobExecutionRow job = findJobOrThrow(jobId);
        ensureConnectionBelongsToWorkspace(job.getConnectionId(), workspaceId);

        return jobItemRepository.findByJobExecutionId(jobId).stream()
                .map(this::toItemResponse)
                .toList();
    }

    @Transactional
    public JobRetryResponse retryJob(long jobId, long workspaceId) {
        JobExecutionRow job = findJobOrThrow(jobId);
        ensureConnectionBelongsToWorkspace(job.getConnectionId(), workspaceId);

        JobExecutionStatus status = JobExecutionStatus.valueOf(job.getStatus());
        if (!RETRYABLE_STATUSES.contains(status)) {
            throw BadRequestException.of(MessageCodes.JOB_NOT_RETRYABLE, jobId, job.getStatus());
        }

        if (jobExecutionRepository.existsActiveForConnection(job.getConnectionId())) {
            throw ConflictException.of(MessageCodes.JOB_ACTIVE_EXISTS, job.getConnectionId());
        }

        String paramsJson = buildRetryParamsJson(jobId);
        long newJobId = jobExecutionRepository.insert(
                job.getConnectionId(), INCREMENTAL_EVENT_TYPE, paramsJson);

        outboxService.createEvent(
                OutboxEventType.ETL_SYNC_EXECUTE,
                "job_execution",
                newJobId,
                Map.of("jobExecutionId", newJobId,
                        "connectionId", job.getConnectionId(),
                        "trigger", "manual_retry",
                        "sourceJobId", jobId));

        log.info("Retry job created: newJobId={}, sourceJobId={}, connectionId={}",
                newJobId, jobId, job.getConnectionId());

        return new JobRetryResponse(newJobId, "Retry job created");
    }

    private String buildRetryParamsJson(long sourceJobId) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("sourceJobId", sourceJobId);
            root.put("trigger", "manual_retry");
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize retry job params", e);
        }
    }

    private JobExecutionRow findJobOrThrow(long jobId) {
        return jobExecutionRepository.findById(jobId)
                .orElseThrow(() -> NotFoundException.of(MessageCodes.JOB_NOT_FOUND, jobId));
    }

    private void ensureConnectionBelongsToWorkspace(long connectionId, long workspaceId) {
        connectionRepository.findByIdAndWorkspaceId(connectionId, workspaceId)
                .orElseThrow(() -> NotFoundException.connection(connectionId));
    }

    private JobExecutionResponse toResponse(JobExecutionRow row) {
        return new JobExecutionResponse(
                row.getId(),
                row.getConnectionId(),
                row.getEventType(),
                row.getStatus(),
                row.getStartedAt(),
                row.getCompletedAt(),
                parseJsonNode(row.getErrorDetails()),
                row.getCreatedAt());
    }

    private JobItemResponse toItemResponse(JobItemRow row) {
        return new JobItemResponse(
                row.getId(),
                row.getSourceId(),
                row.getPageNumber(),
                row.getS3Key(),
                row.getStatus(),
                row.getRecordCount(),
                row.getByteSize(),
                row.getCapturedAt(),
                row.getProcessedAt());
    }

    private JsonNode parseJsonNode(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse error_details JSON: {}", e.getMessage());
            return null;
        }
    }
}
