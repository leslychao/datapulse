package io.datapulse.sellerops.api;

import io.datapulse.sellerops.domain.OfferService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/workspaces/{workspaceId}/offers",
    produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class OfferController {

  private final OfferService offerService;

  @GetMapping("/{offerId}")
  @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
  public OfferDetailResponse getOfferDetail(
      @PathVariable("workspaceId") Long workspaceId,
      @PathVariable("offerId") Long offerId) {
    return offerService.getOfferDetail(workspaceId, offerId);
  }

  @PostMapping("/{offerId}/lock")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
  public void lockOffer(
      @PathVariable("workspaceId") long workspaceId,
      @PathVariable("offerId") long offerId,
      @Valid @RequestBody LockOfferRequest request) {
    offerService.lockOffer(workspaceId, offerId, request);
  }

  @PostMapping("/{offerId}/unlock")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
  public void unlockOffer(
      @PathVariable("workspaceId") long workspaceId,
      @PathVariable("offerId") long offerId) {
    offerService.unlockOffer(workspaceId, offerId);
  }

  @GetMapping("/{offerId}/action-history")
  @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
  public Page<ActionHistoryResponse> getActionHistory(
      @PathVariable("workspaceId") long workspaceId,
      @PathVariable("offerId") long offerId,
      Pageable pageable) {
    return offerService.getActionHistory(workspaceId, offerId, pageable);
  }
}
