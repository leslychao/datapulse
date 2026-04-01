package io.datapulse.promotions.domain;

import io.datapulse.promotions.api.PromoEvaluationMapper;
import io.datapulse.promotions.api.PromoEvaluationResponse;
import io.datapulse.promotions.persistence.PromoEvaluationEntity;
import io.datapulse.promotions.persistence.PromoEvaluationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PromoEvaluationApiService {

    private final PromoEvaluationRepository evaluationRepository;
    private final PromoEvaluationMapper evaluationMapper;

    @Transactional(readOnly = true)
    public Page<PromoEvaluationResponse> listEvaluations(long workspaceId, Long runId,
                                                          PromoEvaluationResult evaluationResult,
                                                          Pageable pageable) {
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
