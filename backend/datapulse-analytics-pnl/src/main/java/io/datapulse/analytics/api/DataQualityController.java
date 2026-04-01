package io.datapulse.analytics.api;

import java.util.List;

import io.datapulse.analytics.domain.DataQualityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/workspaces/{workspaceId}/analytics/data-quality",
    produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class DataQualityController {

    private final DataQualityService dataQualityService;

    @GetMapping("/status")
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
    public DataQualityStatusResponse getStatus(
            @PathVariable("workspaceId") long workspaceId) {
        return dataQualityService.getStatus(workspaceId);
    }

    @GetMapping("/reconciliation")
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)"
        + " and hasAnyAuthority('ROLE_ADMIN', 'ROLE_OWNER')")
    public List<ReconciliationResponse> getReconciliation(
            @PathVariable("workspaceId") long workspaceId) {
        return dataQualityService.getReconciliation(workspaceId);
    }
}
