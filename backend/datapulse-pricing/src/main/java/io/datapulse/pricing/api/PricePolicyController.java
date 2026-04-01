package io.datapulse.pricing.api;

import io.datapulse.pricing.domain.PolicyStatus;
import io.datapulse.pricing.domain.PricePolicyService;
import io.datapulse.platform.security.WorkspaceContext;
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
@RequestMapping(value = "/api/pricing/policies", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class PricePolicyController {

    private final PricePolicyService policyService;
    private final WorkspaceContext workspaceContext;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
    public PricePolicyResponse createPolicy(@Valid @RequestBody CreatePricePolicyRequest request) {
        return policyService.createPolicy(
                request, workspaceContext.getWorkspaceId(), workspaceContext.getUserId());
    }

    @GetMapping
    public List<PricePolicySummaryResponse> listPolicies(
            @RequestParam(value = "status", required = false) PolicyStatus status) {
        return policyService.listPolicies(workspaceContext.getWorkspaceId(), status);
    }

    @GetMapping("/{policyId}")
    public PricePolicyResponse getPolicy(@PathVariable("policyId") Long policyId) {
        return policyService.getPolicy(policyId, workspaceContext.getWorkspaceId());
    }

    @PutMapping("/{policyId}")
    @PreAuthorize("hasAnyAuthority('ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
    public PricePolicyResponse updatePolicy(@PathVariable("policyId") Long policyId,
                                            @Valid @RequestBody UpdatePricePolicyRequest request) {
        return policyService.updatePolicy(policyId, request, workspaceContext.getWorkspaceId());
    }

    @PostMapping("/{policyId}/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyAuthority('ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
    public void activatePolicy(@PathVariable("policyId") Long policyId) {
        policyService.activatePolicy(policyId, workspaceContext.getWorkspaceId());
    }

    @PostMapping("/{policyId}/pause")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyAuthority('ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
    public void pausePolicy(@PathVariable("policyId") Long policyId) {
        policyService.pausePolicy(policyId, workspaceContext.getWorkspaceId());
    }

    @PostMapping("/{policyId}/archive")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyAuthority('ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
    public void archivePolicy(@PathVariable("policyId") Long policyId) {
        policyService.archivePolicy(policyId, workspaceContext.getWorkspaceId());
    }
}
