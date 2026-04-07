package io.datapulse.pricing.api;

import io.datapulse.pricing.domain.PricingInsightService;
import io.datapulse.pricing.persistence.PricingInsightEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/workspaces/{workspaceId}/pricing/insights",
    produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class PricingInsightController {

  private final PricingInsightService insightService;

  @GetMapping
  @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
  public Page<PricingInsightResponse> listInsights(
      @PathVariable("workspaceId") long workspaceId,
      @RequestParam(value = "type", required = false) String insightType,
      @RequestParam(value = "acknowledged", required = false) Boolean acknowledged,
      Pageable pageable) {
    return insightService.listInsights(workspaceId, insightType, acknowledged, pageable)
        .map(PricingInsightController::toResponse);
  }

  @GetMapping("/count")
  @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
  public long countUnacknowledged(@PathVariable("workspaceId") long workspaceId) {
    return insightService.countUnacknowledged(workspaceId);
  }

  @PostMapping("/{insightId}/acknowledge")
  @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void acknowledge(
      @PathVariable("workspaceId") long workspaceId,
      @PathVariable("insightId") long insightId) {
    insightService.acknowledge(insightId, workspaceId);
  }

  private static PricingInsightResponse toResponse(PricingInsightEntity entity) {
    return new PricingInsightResponse(
        entity.getId(),
        entity.getWorkspaceId(),
        entity.getInsightType(),
        entity.getTitle(),
        entity.getBody(),
        entity.getSeverity().name(),
        entity.isAcknowledged(),
        entity.getCreatedAt());
  }
}
