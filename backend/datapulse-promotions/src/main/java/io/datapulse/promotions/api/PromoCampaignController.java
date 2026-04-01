package io.datapulse.promotions.api;

import io.datapulse.platform.security.WorkspaceContext;
import io.datapulse.promotions.domain.PromoCampaignService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/promo/campaigns", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class PromoCampaignController {

    private final PromoCampaignService campaignService;
    private final WorkspaceContext workspaceContext;

    @GetMapping
    public Page<PromoCampaignSummaryResponse> listCampaigns(
            @RequestParam(value = "connectionId", required = false) Long connectionId,
            @RequestParam(value = "status", required = false) String status,
            Pageable pageable) {
        return campaignService.listCampaigns(
                workspaceContext.getWorkspaceId(), connectionId, status, pageable);
    }

    @GetMapping("/{campaignId}")
    public PromoCampaignDetailResponse getCampaign(
            @PathVariable("campaignId") Long campaignId) {
        return campaignService.getCampaign(
                campaignId, workspaceContext.getWorkspaceId());
    }

    @GetMapping("/{campaignId}/products")
    public Page<PromoCampaignProductResponse> getCampaignProducts(
            @PathVariable("campaignId") Long campaignId,
            @RequestParam(value = "participationStatus", required = false) String participationStatus,
            Pageable pageable) {
        return campaignService.getCampaignProducts(
                campaignId, participationStatus, pageable);
    }
}
