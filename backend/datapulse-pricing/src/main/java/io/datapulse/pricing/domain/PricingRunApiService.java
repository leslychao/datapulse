package io.datapulse.pricing.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.datapulse.common.error.MessageCodes;
import io.datapulse.common.exception.BadRequestException;
import io.datapulse.common.exception.NotFoundException;
import io.datapulse.platform.outbox.OutboxEventType;
import io.datapulse.platform.outbox.OutboxService;
import io.datapulse.pricing.api.PricingRunFilter;
import io.datapulse.pricing.api.PricingRunMapper;
import io.datapulse.pricing.api.PricingRunResponse;
import io.datapulse.pricing.persistence.PriceDecisionRepository;
import io.datapulse.pricing.persistence.PricePolicyAssignmentRepository;
import io.datapulse.pricing.persistence.PricePolicyRepository;
import io.datapulse.pricing.persistence.PricingRunEntity;
import io.datapulse.pricing.persistence.PricingRunReadRepository;
import io.datapulse.pricing.persistence.PricingRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PricingRunApiService {

    private static final String PRICING_RUN_AGGREGATE_TYPE = "pricing_run";

    private final PricingRunRepository runRepository;
    private final PricingRunReadRepository runReadRepository;
    private final PriceDecisionRepository decisionRepository;
    private final PricePolicyRepository policyRepository;
    private final PricePolicyAssignmentRepository assignmentRepository;
    private final PricingRunMapper runMapper;
    private final OutboxService outboxService;

    @Transactional
    public PricingRunResponse triggerManualRun(long connectionId, long workspaceId) {
        ensureNoRunInProgress(connectionId);
        return doCreateManualRun(connectionId, workspaceId);
    }

    @Transactional
    public List<PricingRunResponse> triggerManualRunForWorkspace(long workspaceId) {
        List<Long> connectionIds = assignmentRepository
                .findDistinctConnectionIdsWithActivePoliciesForWorkspace(workspaceId);

        if (connectionIds.isEmpty()) {
            throw BadRequestException.of(MessageCodes.PRICING_RUN_NO_ACTIVE_ASSIGNMENTS);
        }

        List<PricingRunResponse> results = new ArrayList<>();
        for (long connectionId : connectionIds) {
            if (runRepository.existsByConnectionIdAndStatus(connectionId, RunStatus.IN_PROGRESS)) {
                log.debug("Manual workspace run: skipping connectionId={} (run in progress)",
                        connectionId);
                continue;
            }
            results.add(doCreateManualRun(connectionId, workspaceId));
        }
        return results;
    }

    private PricingRunResponse doCreateManualRun(long connectionId, long workspaceId) {
        var run = new PricingRunEntity();
        run.setWorkspaceId(workspaceId);
        run.setConnectionId(connectionId);
        run.setTriggerType(RunTriggerType.MANUAL);
        run.setStatus(RunStatus.PENDING);

        PricingRunEntity saved = runRepository.save(run);
        enqueuePricingRunExecute(saved.getId());
        log.info("Manual pricing run triggered: id={}, connectionId={}, workspaceId={}",
                saved.getId(), connectionId, workspaceId);
        return runMapper.toResponse(saved);
    }

    @Transactional
    public void triggerPostSyncRun(long connectionId, long workspaceId,
                                   long sourceJobExecutionId) {
        if (!hasActivePolicies(workspaceId)) {
            log.debug("POST_SYNC run skipped: no active policies, workspaceId={}, connectionId={}",
                    workspaceId, connectionId);
            return;
        }
        if (runRepository.existsByConnectionIdAndStatus(connectionId, RunStatus.IN_PROGRESS)
                || runRepository.existsBySourceJobExecutionId(sourceJobExecutionId)) {
            log.debug("POST_SYNC run skipped: connectionId={}, jobId={} (in-progress or duplicate)",
                    connectionId, sourceJobExecutionId);
            return;
        }

        var run = new PricingRunEntity();
        run.setWorkspaceId(workspaceId);
        run.setConnectionId(connectionId);
        run.setTriggerType(RunTriggerType.POST_SYNC);
        run.setSourceJobExecutionId(sourceJobExecutionId);
        run.setStatus(RunStatus.PENDING);

        PricingRunEntity saved = runRepository.save(run);
        enqueuePricingRunExecute(saved.getId());
        log.info("POST_SYNC pricing run triggered: id={}, connectionId={}, jobId={}",
                saved.getId(), connectionId, sourceJobExecutionId);
    }

    @Transactional
    public void triggerScheduledRun(long connectionId, long workspaceId) {
        if (!hasActivePolicies(workspaceId)) {
            log.debug("Scheduled run skipped: no active policies, workspaceId={}, connectionId={}",
                    workspaceId, connectionId);
            return;
        }
        if (runRepository.existsByConnectionIdAndStatus(connectionId, RunStatus.IN_PROGRESS)) {
            log.debug("Scheduled run skipped: connectionId={} (run in progress)", connectionId);
            return;
        }

        var run = new PricingRunEntity();
        run.setWorkspaceId(workspaceId);
        run.setConnectionId(connectionId);
        run.setTriggerType(RunTriggerType.SCHEDULED);
        run.setStatus(RunStatus.PENDING);

        PricingRunEntity saved = runRepository.save(run);
        enqueuePricingRunExecute(saved.getId());
        log.info("Scheduled pricing run triggered: id={}, connectionId={}", saved.getId(), connectionId);
    }

    @Transactional
    public void triggerPolicyChangeRun(long connectionId, long workspaceId) {
        if (!hasActivePolicies(workspaceId)) {
            log.debug("Policy change run skipped: no active policies, workspaceId={}, connectionId={}",
                    workspaceId, connectionId);
            return;
        }
        if (runRepository.existsByConnectionIdAndStatus(connectionId, RunStatus.IN_PROGRESS)) {
            log.debug("Policy change run skipped: connectionId={} (run in progress)", connectionId);
            return;
        }

        var run = new PricingRunEntity();
        run.setWorkspaceId(workspaceId);
        run.setConnectionId(connectionId);
        run.setTriggerType(RunTriggerType.POLICY_CHANGE);
        run.setStatus(RunStatus.PENDING);

        PricingRunEntity saved = runRepository.save(run);
        enqueuePricingRunExecute(saved.getId());
        log.info("Policy change pricing run triggered: id={}, connectionId={}",
                saved.getId(), connectionId);
    }

    private void enqueuePricingRunExecute(long pricingRunId) {
        outboxService.createEvent(
                OutboxEventType.PRICING_RUN_EXECUTE,
                PRICING_RUN_AGGREGATE_TYPE,
                pricingRunId,
                Map.of("runId", pricingRunId));
    }

    private boolean hasActivePolicies(long workspaceId) {
        return policyRepository.existsByWorkspaceIdAndStatus(workspaceId, PolicyStatus.ACTIVE);
    }

    private void ensureNoRunInProgress(long connectionId) {
        boolean inProgress = runRepository.existsByConnectionIdAndStatus(
                connectionId, RunStatus.IN_PROGRESS);
        if (inProgress) {
            throw BadRequestException.of(MessageCodes.PRICING_RUN_ALREADY_IN_PROGRESS);
        }
    }

    @Transactional(readOnly = true)
    public Page<PricingRunResponse> listRuns(long workspaceId, PricingRunFilter filter,
                                             Pageable pageable) {
        Page<PricingRunEntity> page = runReadRepository.findByFilter(
                workspaceId, filter, pageable);

        List<Long> runIds = page.getContent().stream()
                .map(PricingRunEntity::getId)
                .toList();

        Map<Long, Integer> simCounts = runIds.isEmpty()
                ? Collections.emptyMap()
                : decisionRepository.countSimulatedByRunIds(runIds).stream()
                        .collect(Collectors.toMap(
                                r -> (Long) r[0],
                                r -> ((Number) r[1]).intValue()));

        Map<Long, String> connNames = runReadRepository.findConnectionNames(
                page.getContent().stream()
                        .map(PricingRunEntity::getConnectionId)
                        .collect(Collectors.toSet()));

        return page.map(entity -> runMapper.toResponse(entity)
                .withEnrichment(
                        connNames.getOrDefault(entity.getConnectionId(), ""),
                        simCounts.getOrDefault(entity.getId(), 0)));
    }

    @Transactional(readOnly = true)
    public PricingRunResponse getRun(long runId, long workspaceId) {
        PricingRunEntity entity = runRepository.findByIdAndWorkspaceId(runId, workspaceId)
                .orElseThrow(() -> NotFoundException.entity("PricingRun", runId));

        Map<Long, String> connNames = runReadRepository.findConnectionNames(
                Set.of(entity.getConnectionId()));

        return runMapper.toResponse(entity)
                .withEnrichment(
                        connNames.getOrDefault(entity.getConnectionId(), ""),
                        0);
    }

    @Transactional
    public void resumeRun(long runId, long workspaceId) {
        PricingRunEntity entity = runRepository.findByIdAndWorkspaceId(runId, workspaceId)
                .orElseThrow(() -> NotFoundException.entity("PricingRun", runId));

        if (!entity.getStatus().isResumable()) {
            throw BadRequestException.of(MessageCodes.PRICING_RUN_NOT_RESUMABLE,
                    entity.getStatus().name());
        }

        entity.setStatus(RunStatus.IN_PROGRESS);
        runRepository.save(entity);
        log.info("Pricing run resumed: id={}, workspaceId={}", runId, workspaceId);
    }

    @Transactional
    public void cancelRun(long runId, long workspaceId) {
        PricingRunEntity entity = runRepository.findByIdAndWorkspaceId(runId, workspaceId)
                .orElseThrow(() -> NotFoundException.entity("PricingRun", runId));

        if (entity.getStatus() != RunStatus.PAUSED) {
            throw BadRequestException.of(MessageCodes.PRICING_RUN_NOT_CANCELLABLE,
                    entity.getStatus().name());
        }

        entity.setStatus(RunStatus.CANCELLED);
        runRepository.save(entity);
        log.info("Pricing run cancelled: id={}, workspaceId={}", runId, workspaceId);
    }
}
