package io.datapulse.sellerops.api;

import io.datapulse.sellerops.domain.OfferService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
