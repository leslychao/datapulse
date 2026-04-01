package io.datapulse.pricing.domain;

import io.datapulse.common.error.MessageCodes;
import io.datapulse.common.exception.BadRequestException;
import io.datapulse.common.exception.NotFoundException;
import io.datapulse.pricing.api.PricingRunMapper;
import io.datapulse.pricing.api.PricingRunResponse;
import io.datapulse.pricing.persistence.PricingRunEntity;
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

    private final PricingRunRepository runRepository;
    private final PricingRunMapper runMapper;

    @Transactional
    public PricingRunResponse triggerManualRun(long connectionId, long workspaceId) {
        boolean inProgress = runRepository.existsByConnectionIdAndStatus(connectionId, RunStatus.IN_PROGRESS);
        if (inProgress) {
            throw BadRequestException.of(MessageCodes.PRICING_RUN_ALREADY_IN_PROGRESS);
        }

        var run = new PricingRunEntity();
        run.setWorkspaceId(workspaceId);
        run.setConnectionId(connectionId);
        run.setTriggerType(RunTriggerType.MANUAL);
        run.setStatus(RunStatus.PENDING);

        PricingRunEntity saved = runRepository.save(run);
        log.info("Manual pricing run triggered: id={}, connectionId={}, workspaceId={}",
                saved.getId(), connectionId, workspaceId);

        return runMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<PricingRunResponse> listRuns(long workspaceId, Long connectionId, Pageable pageable) {
        Page<PricingRunEntity> page = connectionId != null
                ? runRepository.findAllByWorkspaceIdAndConnectionId(workspaceId, connectionId, pageable)
                : runRepository.findAllByWorkspaceId(workspaceId, pageable);

        return page.map(runMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public PricingRunResponse getRun(long runId, long workspaceId) {
        PricingRunEntity entity = runRepository.findByIdAndWorkspaceId(runId, workspaceId)
                .orElseThrow(() -> NotFoundException.entity("PricingRun", runId));
        return runMapper.toResponse(entity);
    }
}
