package io.datapulse.analytics.api;

import java.util.List;

import io.datapulse.analytics.domain.PnlQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/workspaces/{workspaceId}/analytics/pnl",
        produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class PnlController {

    private final PnlQueryService pnlQueryService;

    @GetMapping("/summary")
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
    public List<PnlSummaryResponse> getSummary(
            @PathVariable("workspaceId") long workspaceId, PnlFilter filter) {
        return pnlQueryService.getSummary(workspaceId, filter);
    }

    @GetMapping("/by-product")
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
    public Page<ProductPnlResponse> getByProduct(
            @PathVariable("workspaceId") long workspaceId, PnlFilter filter, Pageable pageable) {
        return pnlQueryService.getByProduct(workspaceId, filter, pageable);
    }

    @GetMapping("/by-posting")
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
    public Page<PostingPnlResponse> getByPosting(
            @PathVariable("workspaceId") long workspaceId, PnlFilter filter, Pageable pageable) {
        return pnlQueryService.getByPosting(workspaceId, filter, pageable);
    }

    @GetMapping("/posting/{postingId}/details")
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
    public List<PostingDetailResponse> getPostingDetails(
            @PathVariable("workspaceId") long workspaceId,
            @PathVariable("postingId") String postingId) {
        return pnlQueryService.getPostingDetails(workspaceId, postingId);
    }

    @GetMapping("/trend")
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
    public List<PnlTrendResponse> getTrend(
            @PathVariable("workspaceId") long workspaceId, PnlFilter filter,
            @RequestParam(defaultValue = "MONTHLY") TrendGranularity granularity) {
        return pnlQueryService.getTrend(workspaceId, filter, granularity);
    }
}
