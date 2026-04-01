package io.datapulse.analytics.api;

import java.util.List;

import io.datapulse.analytics.domain.ReturnsAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/workspaces/{workspaceId}/analytics/returns",
    produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class ReturnsController {

    private final ReturnsAnalysisService returnsAnalysisService;

    @GetMapping("/summary")
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
    public List<ReturnsSummaryResponse> getSummary(
            @PathVariable("workspaceId") long workspaceId,
            ReturnsFilter filter) {
        return returnsAnalysisService.getSummary(workspaceId, filter);
    }

    @GetMapping("/by-product")
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
    public Page<ProductReturnResponse> getByProduct(
            @PathVariable("workspaceId") long workspaceId,
            ReturnsFilter filter,
            Pageable pageable) {
        return returnsAnalysisService.getByProduct(workspaceId, filter, pageable);
    }

    @GetMapping("/trend")
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
    public List<ReturnsTrendResponse> getTrend(
            @PathVariable("workspaceId") long workspaceId,
            ReturnsFilter filter) {
        return returnsAnalysisService.getTrend(workspaceId, filter);
    }
}
