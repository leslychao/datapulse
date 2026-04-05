package io.datapulse.promotions.domain;

import io.datapulse.promotions.api.PromoEvaluationMapper;
import io.datapulse.promotions.api.PromoEvaluationResponse;
import io.datapulse.promotions.persistence.PromoEvaluationEntity;
import io.datapulse.promotions.persistence.PromoEvaluationQueryRepository;
import io.datapulse.promotions.persistence.PromoEvaluationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PromoEvaluationApiService {

    private final PromoEvaluationRepository evaluationRepository;
    private final PromoEvaluationQueryRepository evaluationQueryRepository;
    private final PromoEvaluationMapper evaluationMapper;

    public Page<PromoEvaluationResponse> listEvaluations(long workspaceId, Long runId,
                                                          Long campaignId, Long marketplaceOfferId,
                                                          PromoEvaluationResult evaluationResult,
                                                          Pageable pageable) {
        boolean hasExtraFilters = campaignId != null || marketplaceOfferId != null;

        if (hasExtraFilters || (runId != null && evaluationResult != null)) {
            return evaluationQueryRepository.findFiltered(
                    workspaceId, runId, campaignId, marketplaceOfferId,
                    evaluationResult, pageable)
                    .map(evaluationMapper::toResponse);
        }

        Page<PromoEvaluationEntity> page;
        if (runId != null) {
            page = evaluationRepository.findAllByPromoEvaluationRunId(runId, pageable);
        } else if (evaluationResult != null) {
            page = evaluationRepository.findAllByWorkspaceIdAndEvaluationResult(
                    workspaceId, evaluationResult, pageable);
        } else {
            page = evaluationRepository.findAllByWorkspaceId(workspaceId, pageable);
        }

        return page.map(evaluationMapper::toResponse);
    }
}
