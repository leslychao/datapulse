package io.datapulse.promotions.api;

import io.datapulse.promotions.domain.PromoEvaluationRunApiService;
import io.datapulse.promotions.domain.PromoRunStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping(value = "/api/workspaces/{workspaceId}/promo/evaluation-runs",
    produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class PromoEvaluationRunController {

    private final PromoEvaluationRunApiService runApiService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)"
        + " and hasAnyAuthority('ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
    public PromoEvaluationRunResponse triggerRun(
            @PathVariable("workspaceId") long workspaceId,
            @Valid @RequestBody TriggerPromoEvaluationRunRequest request) {
        return runApiService.triggerManualRun(request.connectionId(), workspaceId);
    }

    @GetMapping
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
    public Page<PromoEvaluationRunResponse> listRuns(
            @PathVariable("workspaceId") long workspaceId,
            @RequestParam(value = "connectionId", required = false) Long connectionId,
            @RequestParam(value = "status", required = false) PromoRunStatus status,
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Pageable pageable) {
        return runApiService.listRuns(workspaceId, connectionId, status, from, to, pageable);
    }

    @GetMapping("/{runId}")
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
    public PromoEvaluationRunResponse getRun(
            @PathVariable("workspaceId") long workspaceId,
            @PathVariable("runId") Long runId) {
        return runApiService.getRun(runId, workspaceId);
    }
}
