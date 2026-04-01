package io.datapulse.pricing.api;

import io.datapulse.pricing.domain.PricingRunApiService;
import io.datapulse.platform.security.WorkspaceContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/pricing/runs", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class PricingRunController {

    private final PricingRunApiService runApiService;
    private final WorkspaceContext workspaceContext;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
    public PricingRunResponse triggerRun(@Valid @RequestBody TriggerPricingRunRequest request) {
        return runApiService.triggerManualRun(
                request.connectionId(), workspaceContext.getWorkspaceId());
    }

    @GetMapping
    public Page<PricingRunResponse> listRuns(PricingRunFilter filter, Pageable pageable) {
        return runApiService.listRuns(workspaceContext.getWorkspaceId(), filter, pageable);
    }

    @GetMapping("/{runId}")
    public PricingRunResponse getRun(@PathVariable("runId") Long runId) {
        return runApiService.getRun(runId, workspaceContext.getWorkspaceId());
    }
}
