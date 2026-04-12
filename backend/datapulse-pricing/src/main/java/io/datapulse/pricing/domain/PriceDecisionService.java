package io.datapulse.pricing.domain;

import io.datapulse.common.exception.NotFoundException;
import io.datapulse.pricing.api.PriceDecisionFilter;
import io.datapulse.pricing.api.PriceDecisionMapper;
import io.datapulse.pricing.api.PriceDecisionResponse;
import io.datapulse.pricing.persistence.PriceDecisionEntity;
import io.datapulse.pricing.persistence.PriceDecisionReadRepository;
import io.datapulse.pricing.persistence.PriceDecisionRepository;
import io.datapulse.pricing.persistence.PricingRunReadRepository;
import io.datapulse.pricing.persistence.PricingRunReadRepository.OfferInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PriceDecisionService {

    private final PriceDecisionRepository decisionRepository;
    private final PriceDecisionReadRepository decisionReadRepository;
    private final PriceDecisionMapper decisionMapper;
    private final PricingRunReadRepository runReadRepository;

    @Transactional(readOnly = true)
    public Page<PriceDecisionResponse> listDecisions(long workspaceId, PriceDecisionFilter filter,
                                                     Pageable pageable) {
        Page<PriceDecisionEntity> page = decisionReadRepository.findByFilter(
                workspaceId, filter, pageable);
        Page<PriceDecisionResponse> mapped = page.map(decisionMapper::toResponse);
        return enrichDecisions(mapped);
    }

    @Transactional(readOnly = true)
    public PriceDecisionResponse getDecision(long decisionId, long workspaceId) {
        PriceDecisionEntity entity = decisionRepository.findByIdAndWorkspaceId(decisionId, workspaceId)
                .orElseThrow(() -> NotFoundException.entity("PriceDecision", decisionId));
        PriceDecisionResponse response = decisionMapper.toResponse(entity);
        Page<PriceDecisionResponse> enriched = enrichDecisions(
                new PageImpl<>(List.of(response)));
        return enriched.getContent().get(0);
    }

    private Page<PriceDecisionResponse> enrichDecisions(Page<PriceDecisionResponse> page) {
        if (page.isEmpty()) {
            return page;
        }

        Set<Long> offerIds = page.stream()
                .map(PriceDecisionResponse::marketplaceOfferId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> policyIds = page.stream()
                .map(PriceDecisionResponse::pricePolicyId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, OfferInfo> offerInfoMap = runReadRepository.findOfferInfo(offerIds);
        Map<Long, String> policyNames = runReadRepository.findPolicyNames(policyIds);

        Set<Long> connectionIds = offerInfoMap.values().stream()
                .map(OfferInfo::connectionId)
                .collect(Collectors.toSet());
        Map<Long, String> connectionNames = runReadRepository.findConnectionNames(connectionIds);

        return page.map(d -> {
            OfferInfo offer = offerInfoMap.get(d.marketplaceOfferId());
            String connName = offer != null
                    ? connectionNames.get(offer.connectionId()) : null;
            String polName = d.pricePolicyId() != null
                    ? policyNames.get(d.pricePolicyId()) : null;

            return new PriceDecisionResponse(
                    d.id(), d.pricingRunId(), d.marketplaceOfferId(),
                    d.pricePolicyId(), d.policyVersion(), d.decisionType(),
                    d.currentPrice(), d.targetPrice(),
                    d.priceChangeAmount(), d.priceChangePct(),
                    d.strategyType(), d.strategyRawPrice(),
                    d.signalSnapshot(), d.constraintsApplied(),
                    d.guardsEvaluated(), d.skipReason(),
                    d.explanationSummary(), d.executionMode(), d.createdAt(),
                    offer != null ? offer.name() : null,
                    offer != null ? offer.sellerSku() : null,
                    connName,
                    polName,
                    d.policySnapshot());
        });
    }
}
