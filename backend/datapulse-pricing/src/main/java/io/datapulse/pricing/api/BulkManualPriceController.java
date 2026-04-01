package io.datapulse.pricing.api;

import io.datapulse.pricing.domain.BulkManualPricingService;
import io.datapulse.platform.security.WorkspaceContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/workspaces/{workspaceId}/pricing/bulk-manual",
    produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)"
    + " and hasAnyAuthority('ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
public class BulkManualPriceController {

    private final BulkManualPricingService bulkService;
    private final WorkspaceContext workspaceContext;

    @PostMapping("/preview")
    public BulkManualPreviewResponse preview(
            @PathVariable("workspaceId") long workspaceId,
            @Valid @RequestBody BulkManualPreviewRequest request) {
        return bulkService.preview(request, workspaceId);
    }

    @PostMapping("/apply")
    @ResponseStatus(HttpStatus.CREATED)
    public BulkManualApplyResponse apply(
            @PathVariable("workspaceId") long workspaceId,
            @Valid @RequestBody BulkManualPreviewRequest request) {
        return bulkService.apply(request, workspaceId, workspaceContext.getUserId());
    }
}
