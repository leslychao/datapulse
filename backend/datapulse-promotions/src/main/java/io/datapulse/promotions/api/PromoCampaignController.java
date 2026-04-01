package io.datapulse.promotions.api;

import io.datapulse.promotions.domain.PromoCampaignService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping(value = "/api/workspaces/{workspaceId}/promo/campaigns",
    produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class PromoCampaignController {

    private final PromoCampaignService campaignService;

    @GetMapping
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
    public Page<PromoCampaignSummaryResponse> listCampaigns(
            @PathVariable("workspaceId") long workspaceId,
            @RequestParam(value = "connectionId", required = false) Long connectionId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "marketplaceType", required = false) String marketplaceType,
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Pageable pageable) {
        return campaignService.listCampaigns(
                workspaceId, connectionId, status,
                marketplaceType, from, to, pageable);
    }

    @GetMapping("/{campaignId}")
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
    public PromoCampaignDetailResponse getCampaign(
            @PathVariable("workspaceId") long workspaceId,
            @PathVariable("campaignId") Long campaignId) {
        return campaignService.getCampaign(campaignId, workspaceId);
    }

    @GetMapping("/{campaignId}/products")
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
    public Page<PromoCampaignProductResponse> getCampaignProducts(
            @PathVariable("workspaceId") long workspaceId,
            @PathVariable("campaignId") Long campaignId,
            @RequestParam(value = "participationStatus", required = false) String participationStatus,
            @RequestParam(value = "search", required = false) String search,
            Pageable pageable) {
        return campaignService.getCampaignProducts(
                campaignId, participationStatus, search, pageable);
    }
}
