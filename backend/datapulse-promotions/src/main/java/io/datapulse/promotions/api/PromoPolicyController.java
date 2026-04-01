package io.datapulse.promotions.api;

import io.datapulse.platform.security.WorkspaceContext;
import io.datapulse.promotions.domain.PromoPolicyService;
import io.datapulse.promotions.domain.PromoPolicyStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(value = "/api/workspaces/{workspaceId}/promo/policies",
    produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class PromoPolicyController {

    private final PromoPolicyService policyService;
    private final WorkspaceContext workspaceContext;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
    public PromoPolicyResponse createPolicy(
            @PathVariable("workspaceId") long workspaceId,
            @Valid @RequestBody CreatePromoPolicyRequest request) {
        return policyService.createPolicy(request, workspaceId,
                workspaceContext.getUserId());
    }

    @GetMapping
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
    public List<PromoPolicySummaryResponse> listPolicies(
            @PathVariable("workspaceId") long workspaceId,
            @RequestParam(value = "status", required = false) PromoPolicyStatus status) {
        return policyService.listPolicies(workspaceId, status);
    }

    @GetMapping("/{policyId}")
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
    public PromoPolicyResponse getPolicy(
            @PathVariable("workspaceId") long workspaceId,
            @PathVariable("policyId") Long policyId) {
        return policyService.getPolicy(policyId, workspaceId);
    }

    @PutMapping("/{policyId}")
    @PreAuthorize("hasAnyAuthority('ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
    public PromoPolicyResponse updatePolicy(
            @PathVariable("workspaceId") long workspaceId,
            @PathVariable("policyId") Long policyId,
            @Valid @RequestBody UpdatePromoPolicyRequest request) {
        return policyService.updatePolicy(policyId, request, workspaceId);
    }

    @PostMapping("/{policyId}/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyAuthority('ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
    public void activatePolicy(
            @PathVariable("workspaceId") long workspaceId,
            @PathVariable("policyId") Long policyId) {
        policyService.activatePolicy(policyId, workspaceId);
    }

    @PostMapping("/{policyId}/pause")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyAuthority('ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
    public void pausePolicy(
            @PathVariable("workspaceId") long workspaceId,
            @PathVariable("policyId") Long policyId) {
        policyService.pausePolicy(policyId, workspaceId);
    }

    @PostMapping("/{policyId}/archive")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyAuthority('ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
    public void archivePolicy(
            @PathVariable("workspaceId") long workspaceId,
            @PathVariable("policyId") Long policyId) {
        policyService.archivePolicy(policyId, workspaceId);
    }
}
