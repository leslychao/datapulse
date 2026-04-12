package io.datapulse.bidding.api;

import io.datapulse.bidding.domain.ManualBidLockService;
import io.datapulse.bidding.persistence.ManualBidLockEntity;
import io.datapulse.platform.security.WorkspaceContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(
    value = "/api/workspaces/{workspaceId}/manual-bid-locks",
    produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class ManualBidLockController {

  private final ManualBidLockService lockService;
  private final BidPolicyMapper mapper;
  private final WorkspaceContext workspaceContext;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyAuthority('ROLE_OPERATOR', 'ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
  public ManualBidLockResponse createLock(
      @PathVariable("workspaceId") long workspaceId,
      @Valid @RequestBody CreateManualBidLockRequest request) {

    ManualBidLockEntity entity = lockService.createLock(
        workspaceId,
        request.marketplaceOfferId(),
        request.lockedBid(),
        request.reason(),
        workspaceContext.getUserId(),
        request.expiresAt());

    return mapper.toResponse(entity);
  }

  @PostMapping("/bulk")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyAuthority('ROLE_OPERATOR', 'ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
  public List<ManualBidLockResponse> bulkCreateLocks(
      @PathVariable("workspaceId") long workspaceId,
      @Valid @RequestBody List<CreateManualBidLockRequest> requests) {

    return lockService.bulkCreateLock(
            workspaceId, requests, workspaceContext.getUserId())
        .stream()
        .map(mapper::toResponse)
        .toList();
  }

  @PostMapping("/bulk-unlock")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAnyAuthority('ROLE_OPERATOR', 'ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
  public void bulkRemoveLocks(
      @PathVariable("workspaceId") long workspaceId,
      @Valid @RequestBody BulkUnlockRequest request) {
    lockService.bulkRemoveLock(request.lockIds());
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAnyAuthority('ROLE_OPERATOR', 'ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
  public void removeLock(
      @PathVariable("workspaceId") long workspaceId,
      @PathVariable("id") long id) {
    lockService.removeLock(id);
  }
}
