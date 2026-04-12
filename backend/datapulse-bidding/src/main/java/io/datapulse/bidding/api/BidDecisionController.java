package io.datapulse.bidding.api;

import io.datapulse.bidding.domain.BidDecisionQueryService;
import io.datapulse.bidding.persistence.BidDecisionEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(
    value = "/api/workspaces/{workspaceId}/bid-decisions",
    produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class BidDecisionController {

  private final BidDecisionQueryService decisionQueryService;
  private final BidPolicyMapper mapper;

  @GetMapping
  @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
  public Page<BidDecisionSummaryResponse> listDecisions(
      @PathVariable("workspaceId") long workspaceId,
      @RequestParam(value = "bidPolicyId", required = false) Long bidPolicyId,
      Pageable pageable) {

    return decisionQueryService.listDecisions(workspaceId, bidPolicyId, pageable)
        .map(mapper::toSummary);
  }

  @GetMapping("/{id}")
  @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
  public BidDecisionDetailResponse getDecision(
      @PathVariable("workspaceId") long workspaceId,
      @PathVariable("id") long id) {

    BidDecisionEntity entity = decisionQueryService.getDecision(id);
    return mapper.toDetail(entity);
  }
}
