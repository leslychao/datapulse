package io.datapulse.promotions.api;

import io.datapulse.platform.security.WorkspaceContext;
import io.datapulse.promotions.domain.PromoEvaluationApiService;
import io.datapulse.promotions.domain.PromoEvaluationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/promo/evaluations", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class PromoEvaluationController {

    private final PromoEvaluationApiService evaluationApiService;
    private final WorkspaceContext workspaceContext;

    @GetMapping
    public Page<PromoEvaluationResponse> listEvaluations(
            @RequestParam(value = "runId", required = false) Long runId,
            @RequestParam(value = "campaignId", required = false) Long campaignId,
            @RequestParam(value = "marketplaceOfferId", required = false) Long marketplaceOfferId,
            @RequestParam(value = "evaluationResult", required = false) PromoEvaluationResult evaluationResult,
            Pageable pageable) {
        return evaluationApiService.listEvaluations(
                workspaceContext.getWorkspaceId(), runId, campaignId,
                marketplaceOfferId, evaluationResult, pageable);
    }
}
