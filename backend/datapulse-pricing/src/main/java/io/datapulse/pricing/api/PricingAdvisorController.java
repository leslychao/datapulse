package io.datapulse.pricing.api;

import io.datapulse.common.error.MessageCodes;
import io.datapulse.pricing.domain.PricingAdvisorService;
import io.datapulse.pricing.domain.PricingAdvisorService.AdvisorResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/workspaces/{workspaceId}/pricing/advisor",
    produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class PricingAdvisorController {

  private final PricingAdvisorService advisorService;

  @PostMapping("/{offerId}")
  @PreAuthorize("@workspaceAccessService.canRead(#workspaceId)")
  public AdvisorResponse generateAdvice(
      @PathVariable("workspaceId") long workspaceId,
      @PathVariable("offerId") long offerId) {
    AdvisorResult result = advisorService.generateAdvice(offerId, workspaceId);
    if (!result.isAvailable()) {
      return new AdvisorResponse(null, MessageCodes.PRICING_ADVISOR_UNAVAILABLE,
          null, null);
    }
    return new AdvisorResponse(result.advice(), null,
        result.generatedAt(), result.cachedUntil());
  }
}
