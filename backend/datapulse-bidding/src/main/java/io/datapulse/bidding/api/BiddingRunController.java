package io.datapulse.bidding.api;

import io.datapulse.bidding.domain.BiddingRunApiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(
    value = "/api/workspaces/{workspaceId}/bidding/runs",
    produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class BiddingRunController {

  private final BiddingRunApiService biddingRunApiService;
  private final BidPolicyMapper mapper;

  @GetMapping
  @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
  public Page<BiddingRunSummaryResponse> listRuns(
      @PathVariable("workspaceId") long workspaceId,
      Pageable pageable) {

    return biddingRunApiService.listRuns(workspaceId, pageable)
        .map(mapper::toSummary);
  }

  @GetMapping("/{runId}")
  @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
  public BiddingRunSummaryResponse getRunDetail(
      @PathVariable("workspaceId") long workspaceId,
      @PathVariable("runId") long runId) {

    return mapper.toSummary(biddingRunApiService.getRunDetail(runId));
  }

  @PostMapping
  @PreAuthorize("hasAnyAuthority('ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
  public void triggerRun(
      @PathVariable("workspaceId") long workspaceId,
      @Valid @RequestBody TriggerBiddingRunRequest request) {
    biddingRunApiService.triggerRun(workspaceId, request.bidPolicyId());
  }
}
