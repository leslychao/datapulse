package io.datapulse.tenancy.api;

import io.datapulse.platform.security.WorkspaceContext;
import io.datapulse.tenancy.domain.OnboardingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/tenants", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class TenantController {

    private final OnboardingService onboardingService;
    private final WorkspaceContext workspaceContext;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TenantResponse createTenant(@Valid @RequestBody CreateTenantRequest request) {
        return onboardingService.createTenant(request, workspaceContext.getUserId());
    }

    @GetMapping("/{tenantId}")
    public TenantResponse getTenant(@PathVariable("tenantId") Long tenantId) {
        return onboardingService.getTenant(tenantId);
    }

    @PostMapping("/{tenantId}/workspaces")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkspaceListResponse createWorkspace(@PathVariable("tenantId") Long tenantId,
                                                 @Valid @RequestBody CreateWorkspaceRequest request) {
        return onboardingService.createWorkspace(tenantId, request, workspaceContext.getUserId());
    }
}
