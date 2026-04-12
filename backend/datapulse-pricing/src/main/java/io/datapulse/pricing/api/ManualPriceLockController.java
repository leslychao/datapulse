package io.datapulse.pricing.api;

import io.datapulse.platform.security.WorkspaceContext;
import io.datapulse.pricing.domain.ManualPriceLockService;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/workspaces/{workspaceId}/pricing/locks",
    produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class ManualPriceLockController {

    private final ManualPriceLockService lockService;
    private final WorkspaceContext workspaceContext;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('ROLE_OPERATOR', 'ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
    public ManualLockResponse createLock(
            @PathVariable("workspaceId") long workspaceId,
            @Valid @RequestBody CreateManualLockRequest request) {
        return lockService.createLock(request, workspaceId,
                workspaceContext.getUserId());
    }

    @GetMapping
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
    public Page<ManualLockResponse> listActiveLocks(
        @PathVariable("workspaceId") long workspaceId,
        @RequestParam(value = "marketplaceOfferId", required = false) Long marketplaceOfferId,
        @RequestParam(value = "connectionId", required = false) Long connectionId,
        @RequestParam(value = "search", required = false) String search,
        Pageable pageable) {
        return lockService.listActiveLocks(workspaceId, marketplaceOfferId,
                connectionId, search, pageable);
    }

    @DeleteMapping("/{lockId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyAuthority('ROLE_OPERATOR', 'ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
    public void unlock(
            @PathVariable("workspaceId") long workspaceId,
            @PathVariable("lockId") Long lockId) {
        lockService.unlock(lockId, workspaceId, workspaceContext.getUserId());
    }
}
