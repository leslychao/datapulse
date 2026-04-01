package io.datapulse.analytics.api;

import java.util.List;

import io.datapulse.analytics.domain.PnlQueryService;
import io.datapulse.platform.security.WorkspaceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/analytics/pnl", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class PnlController {

    private final PnlQueryService pnlQueryService;
    private final WorkspaceContext workspaceContext;

    @GetMapping("/summary")
    public List<PnlSummaryResponse> getSummary(PnlFilter filter) {
        return pnlQueryService.getSummary(workspaceContext.getWorkspaceId(), filter);
    }

    @GetMapping("/by-product")
    public Page<ProductPnlResponse> getByProduct(PnlFilter filter, Pageable pageable) {
        return pnlQueryService.getByProduct(workspaceContext.getWorkspaceId(), filter, pageable);
    }

    @GetMapping("/by-posting")
    public Page<PostingPnlResponse> getByPosting(PnlFilter filter, Pageable pageable) {
        return pnlQueryService.getByPosting(workspaceContext.getWorkspaceId(), filter, pageable);
    }

    @GetMapping("/posting/{postingId}/details")
    public List<PostingDetailResponse> getPostingDetails(@PathVariable("postingId") String postingId) {
        return pnlQueryService.getPostingDetails(workspaceContext.getWorkspaceId(), postingId);
    }

    @GetMapping("/trend")
    public List<PnlTrendResponse> getTrend(PnlFilter filter,
                                            @RequestParam(defaultValue = "MONTHLY") TrendGranularity granularity) {
        return pnlQueryService.getTrend(workspaceContext.getWorkspaceId(), filter, granularity);
    }
}
