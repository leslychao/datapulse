package io.datapulse.bidding.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.bidding.domain.BidPolicyService;
import io.datapulse.bidding.domain.BiddingStrategyType;
import io.datapulse.bidding.domain.ExecutionMode;
import io.datapulse.bidding.persistence.BidPolicyAssignmentRepository;
import io.datapulse.bidding.persistence.BidPolicyEntity;
import io.datapulse.platform.security.WorkspaceContext;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/workspaces/{workspaceId}/bidding/policies",
    produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class BidPolicyController {

  private final BidPolicyService policyService;
  private final BidPolicyAssignmentRepository assignmentRepository;
  private final BidPolicyMapper mapper;
  private final WorkspaceContext workspaceContext;
  private final ObjectMapper objectMapper;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyAuthority('ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
  public BidPolicyDetailResponse createPolicy(
      @PathVariable("workspaceId") long workspaceId,
      @Valid @RequestBody CreateBidPolicyRequest request) {

    String configJson = request.config() != null ? request.config().toString() : "{}";
    BidPolicyEntity entity = policyService.createPolicy(
        workspaceId,
        request.name(),
        BiddingStrategyType.valueOf(request.strategyType()),
        ExecutionMode.valueOf(request.executionMode()),
        configJson,
        workspaceContext.getUserId());

    return mapper.toDetail(entity, 0);
  }

  @GetMapping
  @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
  public Page<BidPolicySummaryResponse> listPolicies(
      @PathVariable("workspaceId") long workspaceId,
      Pageable pageable) {

    return policyService.listPolicies(workspaceId, pageable)
        .map(entity -> mapper.toSummary(entity,
            assignmentRepository.countByBidPolicyId(entity.getId())));
  }

  @GetMapping("/{id}")
  @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
  public BidPolicyDetailResponse getPolicy(
      @PathVariable("workspaceId") long workspaceId,
      @PathVariable("id") long id) {

    BidPolicyEntity entity = policyService.getPolicy(id);
    int count = assignmentRepository.countByBidPolicyId(id);
    return mapper.toDetail(entity, count);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyAuthority('ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
  public BidPolicyDetailResponse updatePolicy(
      @PathVariable("workspaceId") long workspaceId,
      @PathVariable("id") long id,
      @Valid @RequestBody UpdateBidPolicyRequest request) {

    String configJson = request.config() != null ? request.config().toString() : "{}";
    BidPolicyEntity entity = policyService.updatePolicy(
        id,
        request.name(),
        ExecutionMode.valueOf(request.executionMode()),
        configJson);

    int count = assignmentRepository.countByBidPolicyId(id);
    return mapper.toDetail(entity, count);
  }

  @PostMapping("/{id}/activate")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAnyAuthority('ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
  public void activatePolicy(
      @PathVariable("workspaceId") long workspaceId,
      @PathVariable("id") long id) {
    policyService.activatePolicy(id);
  }

  @PostMapping("/{id}/pause")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAnyAuthority('ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
  public void pausePolicy(
      @PathVariable("workspaceId") long workspaceId,
      @PathVariable("id") long id) {
    policyService.pausePolicy(id);
  }

  @PostMapping("/{id}/archive")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAnyAuthority('ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
  public void archivePolicy(
      @PathVariable("workspaceId") long workspaceId,
      @PathVariable("id") long id) {
    policyService.archivePolicy(id);
  }
}
