package io.datapulse.promotions.api;

import io.datapulse.platform.security.WorkspaceContext;
import io.datapulse.promotions.domain.PromoActionService;
import io.datapulse.promotions.domain.PromoActionStatus;
import io.datapulse.promotions.domain.PromoActionType;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/workspaces/{workspaceId}/promo/actions",
    produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class PromoActionController {

    private final PromoActionService actionService;
    private final WorkspaceContext workspaceContext;

    @GetMapping
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
    public Page<PromoActionResponse> listActions(
            @PathVariable("workspaceId") long workspaceId,
            @RequestParam(value = "campaignId", required = false) Long campaignId,
            @RequestParam(value = "status", required = false) PromoActionStatus status,
            @RequestParam(value = "actionType", required = false) PromoActionType actionType,
            Pageable pageable) {
        return actionService.listActions(workspaceId, campaignId, status, actionType, pageable);
    }

    @PostMapping("/{actionId}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)"
        + " and hasAnyAuthority('ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
    public void approveAction(
            @PathVariable("workspaceId") long workspaceId,
            @PathVariable("actionId") Long actionId) {
        actionService.approveAction(actionId, workspaceId);
    }

    @PostMapping("/{actionId}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)"
        + " and hasAnyAuthority('ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
    public void rejectAction(
            @PathVariable("workspaceId") long workspaceId,
            @PathVariable("actionId") Long actionId,
            @Valid @RequestBody RejectPromoActionRequest request) {
        actionService.rejectAction(actionId, request.reason(), workspaceId);
    }

    @PostMapping("/{actionId}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)"
        + " and hasAnyAuthority('ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
    public void cancelAction(
            @PathVariable("workspaceId") long workspaceId,
            @PathVariable("actionId") Long actionId,
            @Valid @RequestBody CancelPromoActionRequest request) {
        actionService.cancelAction(actionId, request.cancelReason(), workspaceId);
    }

    @PostMapping("/bulk-approve")
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)"
        + " and hasAnyAuthority('ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
    public BulkPromoActionResponse bulkApprove(
            @PathVariable("workspaceId") long workspaceId,
            @Valid @RequestBody BulkPromoActionRequest request) {
        return actionService.bulkApprove(request, workspaceId);
    }

    @PostMapping("/bulk-reject")
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)"
        + " and hasAnyAuthority('ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
    public BulkPromoActionResponse bulkReject(
            @PathVariable("workspaceId") long workspaceId,
            @Valid @RequestBody BulkRejectPromoActionRequest request) {
        return actionService.bulkReject(
                new BulkPromoActionRequest(request.actionIds()),
                request.reason(), workspaceId);
    }
}
