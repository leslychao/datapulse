package io.datapulse.bidding.api;

import io.datapulse.bidding.domain.BiddingDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(
    value = "/api/workspaces/{workspaceId}/bidding/dashboard",
    produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class BiddingDashboardController {

  private final BiddingDashboardService dashboardService;

  @GetMapping
  @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
  public BiddingDashboardResponse getDashboard(
      @PathVariable("workspaceId") long workspaceId) {
    return dashboardService.getDashboard(workspaceId);
  }
}
