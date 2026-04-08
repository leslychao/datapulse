package io.datapulse.promotions.api;

import io.datapulse.promotions.domain.PromoEvaluationApiService;
import io.datapulse.promotions.domain.PromoEvaluationResult;
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
@RequestMapping(value = "/api/workspaces/{workspaceId}/promo/evaluations",
    produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class PromoEvaluationController {

    private final PromoEvaluationApiService evaluationApiService;

    @GetMapping("/kpi")
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
    public PromoEvaluationKpiResponse getEvaluationKpi(
            @PathVariable("workspaceId") long workspaceId) {
        var row = evaluationApiService.getEvaluationKpi(workspaceId);
        return new PromoEvaluationKpiResponse(
            row.total(), row.profitableCount(),
            row.marginalCount(), row.unprofitableCount());
    }

    @GetMapping
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
    public Page<PromoEvaluationResponse> listEvaluations(
            @PathVariable("workspaceId") long workspaceId,
            @RequestParam(value = "runId", required = false) Long runId,
            @RequestParam(value = "campaignId", required = false) Long campaignId,
            @RequestParam(value = "marketplaceOfferId", required = false) Long marketplaceOfferId,
            @RequestParam(value = "evaluationResult", required = false) PromoEvaluationResult evaluationResult,
            Pageable pageable) {
        return evaluationApiService.listEvaluations(
                workspaceId, runId, campaignId,
                marketplaceOfferId, evaluationResult, pageable);
    }
}
