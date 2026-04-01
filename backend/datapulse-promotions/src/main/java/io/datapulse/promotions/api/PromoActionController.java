package io.datapulse.promotions.api;

import io.datapulse.platform.security.WorkspaceContext;
import io.datapulse.promotions.domain.PromoActionService;
import io.datapulse.promotions.domain.PromoActionStatus;
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
@RequestMapping(value = "/api/promo/actions", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class PromoActionController {

    private final PromoActionService actionService;
    private final WorkspaceContext workspaceContext;

    @GetMapping
    public Page<PromoActionResponse> listActions(
            @RequestParam(value = "campaignId", required = false) Long campaignId,
            @RequestParam(value = "status", required = false) PromoActionStatus status,
            Pageable pageable) {
        return actionService.listActions(
                workspaceContext.getWorkspaceId(), campaignId, status, pageable);
    }

    @PostMapping("/{actionId}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyAuthority('ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
    public void approveAction(@PathVariable("actionId") Long actionId) {
        actionService.approveAction(actionId, workspaceContext.getWorkspaceId());
    }

    @PostMapping("/{actionId}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyAuthority('ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
    public void rejectAction(@PathVariable("actionId") Long actionId,
                             @Valid @RequestBody RejectPromoActionRequest request) {
        actionService.rejectAction(actionId, request.reason(), workspaceContext.getWorkspaceId());
    }

    @PostMapping("/{actionId}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyAuthority('ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
    public void cancelAction(@PathVariable("actionId") Long actionId,
                             @Valid @RequestBody CancelPromoActionRequest request) {
        actionService.cancelAction(
                actionId, request.cancelReason(), workspaceContext.getWorkspaceId());
    }

    @PostMapping("/bulk-approve")
    @PreAuthorize("hasAnyAuthority('ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
    public BulkPromoActionResponse bulkApprove(
            @Valid @RequestBody BulkPromoActionRequest request) {
        return actionService.bulkApprove(request, workspaceContext.getWorkspaceId());
    }

    @PostMapping("/bulk-reject")
    @PreAuthorize("hasAnyAuthority('ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
    public BulkPromoActionResponse bulkReject(
            @Valid @RequestBody BulkRejectPromoActionRequest request) {
        return actionService.bulkReject(
                new BulkPromoActionRequest(request.actionIds()),
                request.reason(),
                workspaceContext.getWorkspaceId());
    }
}
