package io.datapulse.pricing.api;

import io.datapulse.pricing.domain.PricingRunApiService;
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

import java.util.List;

@RestController
@RequestMapping(value = "/api/workspaces/{workspaceId}/pricing/runs",
    produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class PricingRunController {

    private final PricingRunApiService runApiService;

    @PostMapping("/trigger")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId) and hasAnyAuthority('ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
    public List<PricingRunResponse> triggerManualRuns(
            @PathVariable("workspaceId") long workspaceId) {
        return runApiService.triggerManualRunForWorkspace(workspaceId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId) and hasAnyAuthority('ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
    public PricingRunResponse triggerRun(
            @PathVariable("workspaceId") long workspaceId,
            @Valid @RequestBody TriggerPricingRunRequest request) {
        return runApiService.triggerManualRun(request.sourcePlatform(), workspaceId);
    }

    @GetMapping
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
    public Page<PricingRunResponse> listRuns(
            @PathVariable("workspaceId") long workspaceId,
            PricingRunFilter filter,
            Pageable pageable) {
        return runApiService.listRuns(workspaceId, filter, pageable);
    }

    @GetMapping("/{runId}")
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
    public PricingRunResponse getRun(
            @PathVariable("workspaceId") long workspaceId,
            @PathVariable("runId") Long runId) {
        return runApiService.getRun(runId, workspaceId);
    }

    @PostMapping("/{runId}/resume")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId) and hasAnyAuthority('ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
    public void resumeRun(
            @PathVariable("workspaceId") long workspaceId,
            @PathVariable("runId") long runId) {
        runApiService.resumeRun(runId, workspaceId);
    }

    @PostMapping("/{runId}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId) and hasAnyAuthority('ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
    public void cancelRun(
            @PathVariable("workspaceId") long workspaceId,
            @PathVariable("runId") long runId) {
        runApiService.cancelRun(runId, workspaceId);
    }
}
