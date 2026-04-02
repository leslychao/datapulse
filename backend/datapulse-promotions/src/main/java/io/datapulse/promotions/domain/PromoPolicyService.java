package io.datapulse.promotions.domain;

import io.datapulse.common.error.MessageCodes;
import io.datapulse.common.exception.BadRequestException;
import io.datapulse.common.exception.NotFoundException;
import io.datapulse.promotions.api.CreatePromoPolicyRequest;
import io.datapulse.promotions.api.PromoPolicyMapper;
import io.datapulse.promotions.api.PromoPolicyResponse;
import io.datapulse.promotions.api.PromoPolicySummaryResponse;
import io.datapulse.promotions.api.UpdatePromoPolicyRequest;
import io.datapulse.promotions.persistence.PromoPolicyEntity;
import io.datapulse.promotions.persistence.PromoPolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromoPolicyService {

    private final PromoPolicyRepository policyRepository;
    private final PromoPolicyMapper policyMapper;

    @Transactional
    public PromoPolicyResponse createPolicy(CreatePromoPolicyRequest request,
                                            long workspaceId, long userId) {
        var entity = new PromoPolicyEntity();
        entity.setWorkspaceId(workspaceId);
        entity.setName(request.name());
        entity.setStatus(PromoPolicyStatus.DRAFT);
        entity.setParticipationMode(request.participationMode());
        entity.setMinMarginPct(request.minMarginPct());
        entity.setMinStockDaysOfCover(
                request.minStockDaysOfCover() != null ? request.minStockDaysOfCover() : 7);
        entity.setMaxPromoDiscountPct(request.maxPromoDiscountPct());
        entity.setAutoParticipateCategories(request.autoParticipateCategories());
        entity.setAutoDeclineCategories(request.autoDeclineCategories());
        entity.setEvaluationConfig(request.evaluationConfig());
        entity.setVersion(1);
        entity.setCreatedBy(userId);

        PromoPolicyEntity saved = policyRepository.save(entity);
        log.info("Promo policy created: id={}, workspace={}, mode={}",
                saved.getId(), workspaceId, saved.getParticipationMode());

        return policyMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<PromoPolicySummaryResponse> listPolicies(long workspaceId,
                                                         List<PromoPolicyStatus> statuses) {
        List<PromoPolicyEntity> entities = (statuses != null && !statuses.isEmpty())
                ? policyRepository.findAllByWorkspaceIdAndStatusIn(workspaceId, statuses)
                : policyRepository.findAllByWorkspaceId(workspaceId);

        return policyMapper.toSummaries(entities);
    }

    @Transactional(readOnly = true)
    public PromoPolicyResponse getPolicy(long policyId, long workspaceId) {
        PromoPolicyEntity entity = findPolicyOrThrow(policyId, workspaceId);
        return policyMapper.toResponse(entity);
    }

    @Transactional
    public PromoPolicyResponse updatePolicy(long policyId, UpdatePromoPolicyRequest request,
                                            long workspaceId) {
        PromoPolicyEntity entity = findPolicyOrThrow(policyId, workspaceId);

        if (entity.getStatus() == PromoPolicyStatus.ARCHIVED) {
            throw BadRequestException.of(MessageCodes.PROMO_POLICY_ARCHIVED);
        }

        boolean logicChanged = isLogicChanged(entity, request);

        entity.setName(request.name());
        entity.setParticipationMode(request.participationMode());
        entity.setMinMarginPct(request.minMarginPct());
        entity.setMinStockDaysOfCover(
                request.minStockDaysOfCover() != null ? request.minStockDaysOfCover() : entity.getMinStockDaysOfCover());
        entity.setMaxPromoDiscountPct(request.maxPromoDiscountPct());
        entity.setAutoParticipateCategories(request.autoParticipateCategories());
        entity.setAutoDeclineCategories(request.autoDeclineCategories());
        entity.setEvaluationConfig(request.evaluationConfig());

        if (logicChanged) {
            entity.setVersion(entity.getVersion() + 1);
        }

        PromoPolicyEntity saved = policyRepository.save(entity);
        log.info("Promo policy updated: id={}, version={}, logicChanged={}",
                saved.getId(), saved.getVersion(), logicChanged);

        return policyMapper.toResponse(saved);
    }

    @Transactional
    public void activatePolicy(long policyId, long workspaceId) {
        PromoPolicyEntity entity = findPolicyOrThrow(policyId, workspaceId);

        if (entity.getStatus() != PromoPolicyStatus.DRAFT && entity.getStatus() != PromoPolicyStatus.PAUSED) {
            throw BadRequestException.of(MessageCodes.PROMO_POLICY_INVALID_STATE,
                    entity.getStatus().name(), "ACTIVE");
        }

        entity.setStatus(PromoPolicyStatus.ACTIVE);
        policyRepository.save(entity);
        log.info("Promo policy activated: id={}", policyId);
    }

    @Transactional
    public void pausePolicy(long policyId, long workspaceId) {
        PromoPolicyEntity entity = findPolicyOrThrow(policyId, workspaceId);

        if (entity.getStatus() != PromoPolicyStatus.ACTIVE) {
            throw BadRequestException.of(MessageCodes.PROMO_POLICY_INVALID_STATE,
                    entity.getStatus().name(), "PAUSED");
        }

        entity.setStatus(PromoPolicyStatus.PAUSED);
        policyRepository.save(entity);
        log.info("Promo policy paused: id={}", policyId);
    }

    @Transactional
    public void archivePolicy(long policyId, long workspaceId) {
        PromoPolicyEntity entity = findPolicyOrThrow(policyId, workspaceId);

        if (entity.getStatus() == PromoPolicyStatus.ARCHIVED) {
            return;
        }

        entity.setStatus(PromoPolicyStatus.ARCHIVED);
        policyRepository.save(entity);
        log.info("Promo policy archived: id={}", policyId);
    }

    private PromoPolicyEntity findPolicyOrThrow(long policyId, long workspaceId) {
        return policyRepository.findByIdAndWorkspaceId(policyId, workspaceId)
                .orElseThrow(() -> NotFoundException.entity("PromoPolicy", policyId));
    }

    private boolean isLogicChanged(PromoPolicyEntity entity, UpdatePromoPolicyRequest request) {
        return entity.getParticipationMode() != request.participationMode()
                || !Objects.equals(entity.getMinMarginPct(), request.minMarginPct())
                || !Objects.equals(entity.getMinStockDaysOfCover(), request.minStockDaysOfCover())
                || !Objects.equals(entity.getMaxPromoDiscountPct(), request.maxPromoDiscountPct())
                || !Objects.equals(entity.getAutoParticipateCategories(), request.autoParticipateCategories())
                || !Objects.equals(entity.getAutoDeclineCategories(), request.autoDeclineCategories())
                || !Objects.equals(entity.getEvaluationConfig(), request.evaluationConfig());
    }
}
