package io.datapulse.pricing.domain;

import io.datapulse.common.exception.NotFoundException;
import io.datapulse.pricing.api.PriceDecisionFilter;
import io.datapulse.pricing.api.PriceDecisionMapper;
import io.datapulse.pricing.api.PriceDecisionResponse;
import io.datapulse.pricing.persistence.PriceDecisionEntity;
import io.datapulse.pricing.persistence.PriceDecisionReadRepository;
import io.datapulse.pricing.persistence.PriceDecisionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PriceDecisionService {

    private final PriceDecisionRepository decisionRepository;
    private final PriceDecisionReadRepository decisionReadRepository;
    private final PriceDecisionMapper decisionMapper;

    @Transactional(readOnly = true)
    public Page<PriceDecisionResponse> listDecisions(long workspaceId, PriceDecisionFilter filter,
                                                     Pageable pageable) {
        Page<PriceDecisionEntity> page = decisionReadRepository.findByFilter(workspaceId, filter, pageable);
        return page.map(decisionMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public PriceDecisionResponse getDecision(long decisionId, long workspaceId) {
        PriceDecisionEntity entity = decisionRepository.findByIdAndWorkspaceId(decisionId, workspaceId)
                .orElseThrow(() -> NotFoundException.entity("PriceDecision", decisionId));
        return decisionMapper.toResponse(entity);
    }
}
