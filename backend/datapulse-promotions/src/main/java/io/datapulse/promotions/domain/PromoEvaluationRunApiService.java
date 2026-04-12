package io.datapulse.promotions.domain;

import io.datapulse.common.error.MessageCodes;
import io.datapulse.common.exception.BadRequestException;
import io.datapulse.common.exception.NotFoundException;
import io.datapulse.promotions.api.PromoEvaluationRunMapper;
import io.datapulse.promotions.api.PromoEvaluationRunResponse;
import io.datapulse.promotions.persistence.PromoEvaluationRunEntity;
import io.datapulse.promotions.persistence.PromoEvaluationRunQueryRepository;
import io.datapulse.promotions.persistence.PromoEvaluationRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromoEvaluationRunApiService {

    private final PromoEvaluationRunRepository runRepository;
    private final PromoEvaluationRunQueryRepository runQueryRepository;
    private final PromoEvaluationRunMapper runMapper;

    @Transactional
    public PromoEvaluationRunResponse triggerManualRun(String sourcePlatform, long workspaceId) {
        long connectionId = requireConnectionId(workspaceId, sourcePlatform);

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
        log.info("Manual promo evaluation run triggered: runId={}, sourcePlatform={}",
                saved.getId(), sourcePlatform);

        return runMapper.toResponse(saved).withSourcePlatform(sourcePlatform);
    }

    @Transactional(readOnly = true)
    public Page<PromoEvaluationRunResponse> listRuns(long workspaceId, String sourcePlatform,
                                                      PromoRunStatus status, LocalDate from,
                                                      LocalDate to, Pageable pageable) {
        Long connectionId = runQueryRepository.resolveConnectionId(workspaceId, sourcePlatform);
        if (sourcePlatform != null && connectionId == null) {
            return Page.empty(pageable);
        }

        boolean hasDateFilters = from != null || to != null;

        Page<PromoEvaluationRunEntity> page;
        if (hasDateFilters) {
            page = runQueryRepository.findFiltered(
                    workspaceId, connectionId, status, from, to, pageable);
        } else if (connectionId != null) {
            page = runRepository.findAllByWorkspaceIdAndConnectionId(
                    workspaceId, connectionId, pageable);
        } else if (status != null) {
            page = runRepository.findAllByWorkspaceIdAndStatus(workspaceId, status, pageable);
        } else {
            page = runRepository.findAllByWorkspaceId(workspaceId, pageable);
        }

        return enrichWithSourcePlatform(page);
    }

    private long requireConnectionId(long workspaceId, String sourcePlatform) {
        Long connectionId = runQueryRepository.resolveConnectionId(workspaceId, sourcePlatform);
        if (connectionId == null) {
            throw NotFoundException.entity("MarketplaceConnection", sourcePlatform);
        }
        return connectionId;
    }

    @Transactional(readOnly = true)
    public PromoEvaluationRunResponse getRun(long runId, long workspaceId) {
        PromoEvaluationRunEntity entity = runRepository.findByIdAndWorkspaceId(runId, workspaceId)
                .orElseThrow(() -> NotFoundException.entity("PromoEvaluationRun", runId));
        Map<Long, String> types = runQueryRepository.findConnectionMarketplaceTypes(
                Set.of(entity.getConnectionId()));
        return runMapper.toResponse(entity)
                .withSourcePlatform(types.getOrDefault(entity.getConnectionId(), ""));
    }

    private Page<PromoEvaluationRunResponse> enrichWithSourcePlatform(
        Page<PromoEvaluationRunEntity> page) {
        Set<Long> connectionIds = page.getContent().stream()
                .map(PromoEvaluationRunEntity::getConnectionId)
                .collect(Collectors.toSet());
        Map<Long, String> types = runQueryRepository.findConnectionMarketplaceTypes(
                connectionIds);
        return page.map(entity -> runMapper.toResponse(entity)
                .withSourcePlatform(
                    types.getOrDefault(entity.getConnectionId(), "")));
    }
}
