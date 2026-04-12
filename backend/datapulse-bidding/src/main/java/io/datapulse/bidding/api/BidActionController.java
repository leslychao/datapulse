package io.datapulse.bidding.api;

import io.datapulse.bidding.domain.BidActionApprovalService;
import io.datapulse.bidding.domain.BidActionStatus;
import io.datapulse.bidding.persistence.BidActionEntity;
import io.datapulse.bidding.persistence.BidActionRepository;
import io.datapulse.bidding.persistence.BidDecisionEntity;
import io.datapulse.bidding.persistence.BidDecisionRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(
    value = "/api/workspaces/{workspaceId}/bidding/actions",
    produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class BidActionController {

  private final BidActionApprovalService approvalService;
  private final BidActionRepository actionRepository;
  private final BidDecisionRepository decisionRepository;

  @GetMapping
  @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
  public Page<BidActionSummaryResponse> listPending(
      @PathVariable("workspaceId") long workspaceId,
      Pageable pageable) {

    return actionRepository
        .findByWorkspaceIdAndStatus(
            workspaceId, BidActionStatus.PENDING_APPROVAL, pageable)
        .map(this::toSummary);
  }

  @PostMapping("/{id}/approve")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAnyAuthority('ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
  public void approve(
      @PathVariable("workspaceId") long workspaceId,
      @PathVariable("id") long id) {
    approvalService.approve(id);
  }

  @PostMapping("/{id}/reject")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAnyAuthority('ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
  public void reject(
      @PathVariable("workspaceId") long workspaceId,
      @PathVariable("id") long id) {
    approvalService.reject(id);
  }

  @PostMapping("/bulk-approve")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAnyAuthority('ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
  public void bulkApprove(
      @PathVariable("workspaceId") long workspaceId,
      @Valid @RequestBody BulkActionRequest request) {
    approvalService.bulkApprove(request.actionIds());
  }

  @PostMapping("/bulk-reject")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAnyAuthority('ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
  public void bulkReject(
      @PathVariable("workspaceId") long workspaceId,
      @Valid @RequestBody BulkActionRequest request) {
    approvalService.bulkReject(request.actionIds());
  }

  private BidActionSummaryResponse toSummary(BidActionEntity action) {
    String decisionType = decisionRepository.findById(action.getBidDecisionId())
        .map(BidDecisionEntity::getDecisionType)
        .map(Enum::name)
        .orElse("UNKNOWN");

    return new BidActionSummaryResponse(
        action.getId(),
        action.getMarketplaceOfferId(),
        action.getMarketplaceType(),
        decisionType,
        action.getPreviousBid(),
        action.getTargetBid(),
        action.getStatus().name(),
        action.getExecutionMode(),
        action.getCreatedAt().toInstant());
  }
}
