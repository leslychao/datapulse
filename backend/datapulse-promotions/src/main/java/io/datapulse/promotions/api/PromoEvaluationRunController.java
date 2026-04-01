package io.datapulse.promotions.api;

import io.datapulse.platform.security.WorkspaceContext;
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
@RequestMapping(value = "/api/promo/evaluation-runs", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class PromoEvaluationRunController {

    private final PromoEvaluationRunApiService runApiService;
    private final WorkspaceContext workspaceContext;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
    public PromoEvaluationRunResponse triggerRun(
            @Valid @RequestBody TriggerPromoEvaluationRunRequest request) {
        return runApiService.triggerManualRun(
                request.connectionId(), workspaceContext.getWorkspaceId());
    }

    @GetMapping
    public Page<PromoEvaluationRunResponse> listRuns(
            @RequestParam(value = "connectionId", required = false) Long connectionId,
            @RequestParam(value = "status", required = false) PromoRunStatus status,
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Pageable pageable) {
        return runApiService.listRuns(
                workspaceContext.getWorkspaceId(), connectionId, status,
                from, to, pageable);
    }

    @GetMapping("/{runId}")
    public PromoEvaluationRunResponse getRun(@PathVariable("runId") Long runId) {
        return runApiService.getRun(runId, workspaceContext.getWorkspaceId());
    }
}
