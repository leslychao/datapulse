package io.datapulse.promotions.domain;

import io.datapulse.common.exception.NotFoundException;
import io.datapulse.promotions.api.PromoCampaignDetailResponse;
import io.datapulse.promotions.api.PromoCampaignProductResponse;
import io.datapulse.promotions.api.PromoCampaignSummaryResponse;
import io.datapulse.promotions.persistence.PromoCampaignKpiRow;
import io.datapulse.promotions.persistence.PromoCampaignQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PromoCampaignService {

    private final PromoCampaignQueryRepository campaignQueryRepository;

    @Transactional(readOnly = true)
    public PromoCampaignKpiRow getCampaignKpi(long workspaceId) {
        return campaignQueryRepository.findKpi(workspaceId);
    }

    @Transactional(readOnly = true)
    public Page<PromoCampaignSummaryResponse> listCampaigns(long workspaceId,
                                                             List<String> statuses,
                                                             List<String> marketplaceTypes,
                                                             LocalDate from, LocalDate to,
                                                             Pageable pageable) {
        return campaignQueryRepository.listCampaigns(
                workspaceId, statuses, marketplaceTypes, from, to, pageable);
    }

    @Transactional(readOnly = true)
    public PromoCampaignDetailResponse getCampaign(long campaignId, long workspaceId) {
        return campaignQueryRepository.getCampaign(campaignId, workspaceId)
                .orElseThrow(() -> NotFoundException.entity("CanonicalPromoCampaign", campaignId));
    }

    @Transactional(readOnly = true)
    public Page<PromoCampaignProductResponse> getCampaignProducts(
            long campaignId,
            List<String> participationStatuses,
            List<String> evaluationResults,
            List<String> decisionTypes,
            List<String> actionStatuses,
            String search,
            Pageable pageable) {
        return campaignQueryRepository.getCampaignProducts(
                campaignId, participationStatuses, evaluationResults,
                decisionTypes, actionStatuses, search, pageable);
    }
}
