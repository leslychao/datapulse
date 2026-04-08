package io.datapulse.analytics.api;

import java.util.Map;
import java.util.regex.Pattern;

import io.datapulse.analytics.domain.DataQualityService;
import io.datapulse.common.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/workspaces/{workspaceId}/analytics/data-quality",
    produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class DataQualityController {

  private final DataQualityService dataQualityService;

  @GetMapping("/ch-health")
  @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
  public Map<String, Boolean> getClickHouseHealth(
      @PathVariable("workspaceId") long workspaceId) {
    return Map.of("available", dataQualityService.isClickHouseAvailable());
  }

  @GetMapping("/status")
  @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
  public DataQualityStatusResponse getStatus(
      @PathVariable("workspaceId") long workspaceId) {
    return dataQualityService.getStatus(workspaceId);
  }

  @GetMapping("/reconciliation")
  @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)"
      + " and hasAnyAuthority('ROLE_ADMIN', 'ROLE_OWNER')")
  public ReconciliationResultResponse getReconciliation(
      @PathVariable("workspaceId") long workspaceId,
      @RequestParam(value = "period", required = false) String period) {
    Integer periodInt = parsePeriod(period);
    return dataQualityService.getReconciliation(workspaceId, periodInt);
  }

  private static final Pattern PERIOD_PATTERN = Pattern.compile("^\\d{4}-(0[1-9]|1[0-2])$");

  private static Integer parsePeriod(String period) {
    if (period == null || period.isBlank()) return null;
    if (!PERIOD_PATTERN.matcher(period).matches()) {
      throw BadRequestException.of("validation.failed", "period");
    }
    return Integer.parseInt(period.replace("-", ""));
  }
}
