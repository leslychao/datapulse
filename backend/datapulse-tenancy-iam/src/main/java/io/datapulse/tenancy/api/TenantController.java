package io.datapulse.tenancy.api;

import io.datapulse.platform.security.WorkspaceContext;
import io.datapulse.tenancy.domain.OnboardingService;
import io.datapulse.tenancy.domain.WorkspaceSummary;
import io.datapulse.tenancy.persistence.TenantEntity;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
@RequestMapping(value = "/api/tenants", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class TenantController {

    private final OnboardingService onboardingService;
    private final WorkspaceContext workspaceContext;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public TenantResponse createTenant(@Valid @RequestBody CreateTenantRequest request) {
        TenantEntity tenant = onboardingService.createTenant(
                request.name(), workspaceContext.getUserId());
        return toResponse(tenant);
    }

    @GetMapping("/{tenantId}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_OWNER')")
    public TenantResponse getTenant(@PathVariable("tenantId") Long tenantId) {
        TenantEntity tenant = onboardingService.getTenant(tenantId);
        return toResponse(tenant);
    }

    @PostMapping("/{tenantId}/workspaces")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public WorkspaceListResponse createWorkspace(@PathVariable("tenantId") Long tenantId,
                                                 @Valid @RequestBody CreateWorkspaceRequest request) {
        WorkspaceSummary ws = onboardingService.createWorkspace(
                tenantId, request.name(), workspaceContext.getUserId());
        return toListResponse(ws);
    }

    private TenantResponse toResponse(TenantEntity entity) {
        return new TenantResponse(entity.getId(), entity.getName(), entity.getSlug());
    }

    private WorkspaceListResponse toListResponse(WorkspaceSummary ws) {
        return new WorkspaceListResponse(
                ws.id(), ws.name(), ws.slug(), ws.status(),
                ws.tenantId(), ws.tenantName(),
                ws.connectionsCount(), ws.membersCount());
    }
}
