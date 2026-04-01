package io.datapulse.analytics.api;

import java.util.List;

import io.datapulse.analytics.domain.DataQualityService;
import io.datapulse.platform.security.WorkspaceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/analytics/data-quality", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class DataQualityController {

    private final DataQualityService dataQualityService;
    private final WorkspaceContext workspaceContext;

    @GetMapping("/status")
    public DataQualityStatusResponse getStatus() {
        return dataQualityService.getStatus(workspaceContext.getWorkspaceId());
    }

    @GetMapping("/reconciliation")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_OWNER')")
    public List<ReconciliationResponse> getReconciliation() {
        return dataQualityService.getReconciliation(workspaceContext.getWorkspaceId());
    }
}
