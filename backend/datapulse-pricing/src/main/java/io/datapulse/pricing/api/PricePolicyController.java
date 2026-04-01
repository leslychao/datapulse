package io.datapulse.pricing.api;

import io.datapulse.platform.security.WorkspaceContext;
import io.datapulse.pricing.domain.PolicyStatus;
import io.datapulse.pricing.domain.PolicyType;
import io.datapulse.pricing.domain.PricePolicyService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/workspaces/{workspaceId}/pricing/policies",
    produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class PricePolicyController {

    private final PricePolicyService policyService;
    private final WorkspaceContext workspaceContext;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
    public PricePolicyResponse createPolicy(
            @PathVariable("workspaceId") long workspaceId,
            @Valid @RequestBody CreatePricePolicyRequest request) {
        return policyService.createPolicy(request, workspaceId,
                workspaceContext.getUserId());
    }

    @GetMapping
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
    public Page<PricePolicySummaryResponse> listPolicies(
            @PathVariable("workspaceId") long workspaceId,
            @RequestParam(value = "status", required = false) List<PolicyStatus> statuses,
            @RequestParam(value = "strategyType", required = false) PolicyType strategyType,
            Pageable pageable) {
        return policyService.listPoliciesPaged(workspaceId, statuses, strategyType, pageable);
    }

    @GetMapping("/{policyId}")
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
    public PricePolicyResponse getPolicy(
            @PathVariable("workspaceId") long workspaceId,
            @PathVariable("policyId") Long policyId) {
        return policyService.getPolicy(policyId, workspaceId);
    }

    @PutMapping("/{policyId}")
    @PreAuthorize("hasAnyAuthority('ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
    public PricePolicyResponse updatePolicy(
            @PathVariable("workspaceId") long workspaceId,
            @PathVariable("policyId") Long policyId,
            @Valid @RequestBody UpdatePricePolicyRequest request) {
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

    @DeleteMapping("/{policyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyAuthority('ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
    public void deletePolicy(
            @PathVariable("workspaceId") long workspaceId,
            @PathVariable("policyId") Long policyId) {
        policyService.archivePolicy(policyId, workspaceId);
    }
}
