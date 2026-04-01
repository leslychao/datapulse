package io.datapulse.promotions.domain;

import io.datapulse.common.error.MessageCodes;
import io.datapulse.common.exception.BadRequestException;
import io.datapulse.common.exception.NotFoundException;
import io.datapulse.promotions.api.PromoEvaluationRunMapper;
import io.datapulse.promotions.api.PromoEvaluationRunResponse;
import io.datapulse.promotions.persistence.PromoEvaluationRunEntity;
import io.datapulse.promotions.persistence.PromoEvaluationRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromoEvaluationRunApiService {

    private final PromoEvaluationRunRepository runRepository;
    private final PromoEvaluationRunMapper runMapper;

    @Transactional
    public PromoEvaluationRunResponse triggerManualRun(long connectionId, long workspaceId) {
        boolean inProgress = runRepository.existsByConnectionIdAndStatus(
                connectionId, PromoRunStatus.IN_PROGRESS);
        if (inProgress) {
            throw BadRequestException.of(MessageCodes.PROMO_RUN_ALREADY_IN_PROGRESS);
        }

        var run = new PromoEvaluationRunEntity();
        run.setWorkspaceId(workspaceId);
        run.setConnectionId(connectionId);
        run.setTriggerType(PromoRunTriggerType.MANUAL);
        run.setStatus(PromoRunStatus.PENDING);

        PromoEvaluationRunEntity saved = runRepository.save(run);
        log.info("Manual promo evaluation run triggered: runId={}, connectionId={}",
                saved.getId(), connectionId);

        return runMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<PromoEvaluationRunResponse> listRuns(long workspaceId, Long connectionId,
                                                      PromoRunStatus status, Pageable pageable) {
        Page<PromoEvaluationRunEntity> page;

        if (connectionId != null) {
            page = runRepository.findAllByWorkspaceIdAndConnectionId(
                    workspaceId, connectionId, pageable);
        } else if (status != null) {
            page = runRepository.findAllByWorkspaceIdAndStatus(workspaceId, status, pageable);
        } else {
            page = runRepository.findAllByWorkspaceId(workspaceId, pageable);
        }

        return page.map(runMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public PromoEvaluationRunResponse getRun(long runId, long workspaceId) {
        PromoEvaluationRunEntity entity = runRepository.findByIdAndWorkspaceId(runId, workspaceId)
                .orElseThrow(() -> NotFoundException.entity("PromoEvaluationRun", runId));
        return runMapper.toResponse(entity);
    }
}
