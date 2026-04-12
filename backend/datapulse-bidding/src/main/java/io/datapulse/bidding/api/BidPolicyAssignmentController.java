package io.datapulse.bidding.api;

import io.datapulse.bidding.domain.AssignmentScope;
import io.datapulse.bidding.domain.BidPolicyAssignmentService;
import io.datapulse.bidding.persistence.BidPolicyAssignmentEntity;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(
    value = "/api/workspaces/{workspaceId}/bid-policies/{policyId}/assignments",
    produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class BidPolicyAssignmentController {

  private final BidPolicyAssignmentService assignmentService;
  private final BidPolicyMapper mapper;

  @GetMapping
  @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
  public Page<AssignmentResponse> listAssignments(
      @PathVariable("workspaceId") long workspaceId,
      @PathVariable("policyId") long policyId,
      Pageable pageable) {

    return assignmentService.listAssignments(policyId, pageable)
        .map(mapper::toResponse);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyAuthority('ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
  public AssignmentResponse assign(
      @PathVariable("workspaceId") long workspaceId,
      @PathVariable("policyId") long policyId,
      @Valid @RequestBody CreateAssignmentRequest request) {

    BidPolicyAssignmentEntity entity = assignmentService.assign(
        policyId,
        workspaceId,
        request.marketplaceOfferId(),
        request.campaignExternalId(),
        AssignmentScope.valueOf(request.scope()));

    return mapper.toResponse(entity);
  }

  @PostMapping("/bulk")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyAuthority('ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
  public List<AssignmentResponse> bulkAssign(
      @PathVariable("workspaceId") long workspaceId,
      @PathVariable("policyId") long policyId,
      @Valid @RequestBody BulkAssignRequest request) {

    return assignmentService.bulkAssign(
            policyId,
            workspaceId,
            request.marketplaceOfferIds(),
            AssignmentScope.valueOf(request.scope()))
        .stream()
        .map(mapper::toResponse)
        .toList();
  }

  @DeleteMapping("/{assignmentId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAnyAuthority('ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
  public void unassign(
      @PathVariable("workspaceId") long workspaceId,
      @PathVariable("policyId") long policyId,
      @PathVariable("assignmentId") long assignmentId) {
    assignmentService.unassign(assignmentId);
  }
}
