package io.datapulse.bidding.api;

import io.datapulse.bidding.domain.WorkspaceBiddingSettingsService;
import io.datapulse.bidding.persistence.WorkspaceBiddingSettingsEntity;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/workspaces/{workspaceId}/bidding/settings",
    produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class WorkspaceBiddingSettingsController {

  private final WorkspaceBiddingSettingsService settingsService;

  @GetMapping
  @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
  public WorkspaceBiddingSettingsResponse getSettings(
      @PathVariable("workspaceId") long workspaceId) {
    var entity = settingsService.getSettings(workspaceId);
    return toResponse(entity);
  }

  @PutMapping
  @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_OWNER')")
  public WorkspaceBiddingSettingsResponse updateSettings(
      @PathVariable("workspaceId") long workspaceId,
      @Valid @RequestBody UpdateWorkspaceBiddingSettingsRequest request) {
    var entity = settingsService.updateSettings(
        workspaceId,
        request.biddingEnabled(),
        request.maxAggregateDailySpend(),
        request.minDecisionIntervalHours());
    return toResponse(entity);
  }

  private WorkspaceBiddingSettingsResponse toResponse(
      WorkspaceBiddingSettingsEntity entity) {
    return new WorkspaceBiddingSettingsResponse(
        entity.isBiddingEnabled(),
        entity.getMaxAggregateDailySpend(),
        entity.getMinDecisionIntervalHours());
  }
}
