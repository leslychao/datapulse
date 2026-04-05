package io.datapulse.analytics.api;

import io.datapulse.analytics.domain.AdvertisingCampaignService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/workspaces/{workspaceId}/advertising",
    produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class AdvertisingController {

  private final AdvertisingCampaignService campaignService;

  @GetMapping("/campaigns")
  @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
  public Page<CampaignSummaryResponse> getCampaigns(
      @PathVariable("workspaceId") long workspaceId,
      CampaignDashboardFilter filter,
      Pageable pageable) {
    return campaignService.getCampaigns(workspaceId, filter, pageable);
  }
}
