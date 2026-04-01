package io.datapulse.analytics.api;

import java.util.List;

import io.datapulse.analytics.domain.ReturnsAnalysisService;
import io.datapulse.platform.security.WorkspaceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/analytics/returns", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class ReturnsController {

    private final ReturnsAnalysisService returnsAnalysisService;
    private final WorkspaceContext workspaceContext;

    @GetMapping("/summary")
    public List<ReturnsSummaryResponse> getSummary(ReturnsFilter filter) {
        return returnsAnalysisService.getSummary(workspaceContext.getWorkspaceId(), filter);
    }

    @GetMapping("/by-product")
    public Page<ProductReturnResponse> getByProduct(ReturnsFilter filter, Pageable pageable) {
        return returnsAnalysisService.getByProduct(
                workspaceContext.getWorkspaceId(), filter, pageable);
    }

    @GetMapping("/trend")
    public List<ReturnsTrendResponse> getTrend(ReturnsFilter filter) {
        return returnsAnalysisService.getTrend(workspaceContext.getWorkspaceId(), filter);
    }
}
