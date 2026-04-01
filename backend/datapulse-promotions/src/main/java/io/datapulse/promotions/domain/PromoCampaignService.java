package io.datapulse.promotions.domain;

import io.datapulse.common.exception.NotFoundException;
import io.datapulse.promotions.api.PromoCampaignDetailResponse;
import io.datapulse.promotions.api.PromoCampaignProductResponse;
import io.datapulse.promotions.api.PromoCampaignSummaryResponse;
import io.datapulse.promotions.persistence.PromoCampaignQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PromoCampaignService {

    private final PromoCampaignQueryRepository campaignQueryRepository;

    @Transactional(readOnly = true)
    public Page<PromoCampaignSummaryResponse> listCampaigns(long workspaceId, Long connectionId,
                                                             String status, Pageable pageable) {
        return campaignQueryRepository.listCampaigns(workspaceId, connectionId, status, pageable);
    }

    @Transactional(readOnly = true)
    public PromoCampaignDetailResponse getCampaign(long campaignId, long workspaceId) {
        return campaignQueryRepository.getCampaign(campaignId, workspaceId)
                .orElseThrow(() -> NotFoundException.entity("CanonicalPromoCampaign", campaignId));
    }

    @Transactional(readOnly = true)
    public Page<PromoCampaignProductResponse> getCampaignProducts(long campaignId,
                                                                    String participationStatus,
                                                                    Pageable pageable) {
        return campaignQueryRepository.getCampaignProducts(
                campaignId, participationStatus, pageable);
    }
}
