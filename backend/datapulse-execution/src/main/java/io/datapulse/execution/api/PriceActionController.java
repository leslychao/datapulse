package io.datapulse.execution.api;

import io.datapulse.execution.domain.ActionService;
import io.datapulse.execution.domain.PriceActionFilter;
import io.datapulse.execution.domain.ReconciliationService;
import io.datapulse.execution.persistence.PriceActionAttemptEntity;
import io.datapulse.execution.persistence.PriceActionEntity;
import io.datapulse.execution.persistence.PriceActionSummaryRow;
import io.datapulse.platform.security.WorkspaceContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(value = "/api/actions", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class PriceActionController {

    private final ActionService actionService;
    private final ReconciliationService reconciliationService;
    private final WorkspaceContext workspaceContext;

    @GetMapping
    public Page<PriceActionSummaryResponse> list(PriceActionFilter filter,
                                                  Pageable pageable) {
        return actionService.listActions(
                workspaceContext.getWorkspaceId(), filter, pageable)
                .map(this::toSummaryResponse);
    }

    @GetMapping("/{actionId}")
    public PriceActionResponse get(@PathVariable("actionId") long actionId) {
        return toResponse(actionService.getAction(actionId));
    }

    @GetMapping("/{actionId}/attempts")
    public List<PriceActionAttemptResponse> listAttempts(
            @PathVariable("actionId") long actionId) {
        return actionService.getAttempts(actionId).stream()
                .map(this::toAttemptResponse)
                .toList();
    }

    @PostMapping("/{actionId}/approve")
    @PreAuthorize("hasAnyAuthority('ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
    public void approve(@PathVariable("actionId") long actionId) {
        actionService.casApprove(actionId, workspaceContext.getUserId());
    }

    @PostMapping("/bulk-approve")
    @PreAuthorize("hasAnyAuthority('ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
    public void bulkApprove(@Valid @RequestBody BulkApproveRequest request) {
        for (long actionId : request.actionIds()) {
            try {
                actionService.casApprove(actionId, workspaceContext.getUserId());
            } catch (Exception e) {
                // CAS conflicts and not-found are expected during bulk operations
            }
        }
    }

    @PostMapping("/{actionId}/reject")
    @PreAuthorize("hasAnyAuthority('ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
    public void reject(@PathVariable("actionId") long actionId,
                        @Valid @RequestBody CancelRequest request) {
        actionService.casReject(actionId, request.cancelReason());
    }

    @PostMapping("/{actionId}/hold")
    @PreAuthorize("hasAnyAuthority('ROLE_OPERATOR', 'ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
    public void hold(@PathVariable("actionId") long actionId,
                      @Valid @RequestBody HoldRequest request) {
        actionService.casHold(actionId, request.holdReason());
    }

    @PostMapping("/{actionId}/resume")
    @PreAuthorize("hasAnyAuthority('ROLE_OPERATOR', 'ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
    public void resume(@PathVariable("actionId") long actionId) {
        actionService.casResume(actionId, workspaceContext.getUserId());
    }

    @PostMapping("/{actionId}/cancel")
    @PreAuthorize("hasAnyAuthority('ROLE_OPERATOR', 'ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
    public void cancel(@PathVariable("actionId") long actionId,
                        @Valid @RequestBody CancelRequest request) {
        actionService.casCancel(actionId, request.cancelReason());
    }

    @PostMapping("/{actionId}/retry")
    @PreAuthorize("hasAnyAuthority('ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
    public void retry(@PathVariable("actionId") long actionId,
                       @Valid @RequestBody RetryRequest request) {
        actionService.retryFailed(actionId);
    }

    @PostMapping("/{actionId}/reconcile")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_OWNER')")
    public void reconcile(@PathVariable("actionId") long actionId,
                           @Valid @RequestBody ReconcileRequest request) {
        boolean succeeded =
                request.outcome() == ReconcileRequest.ReconcileOutcome.SUCCEEDED;
        reconciliationService.processManualReconciliation(
                actionId, succeeded, request.manualOverrideReason(),
                workspaceContext.getUserId());
    }

    private PriceActionSummaryResponse toSummaryResponse(PriceActionSummaryRow row) {
        return new PriceActionSummaryResponse(
                row.id(), row.marketplaceOfferId(),
                row.executionMode(), row.status(),
                row.targetPrice(), row.currentPriceAtCreation(),
                row.attemptCount(), row.maxAttempts(),
                row.createdAt(), row.updatedAt()
        );
    }

    private PriceActionResponse toResponse(PriceActionEntity e) {
        return new PriceActionResponse(
                e.getId(), e.getWorkspaceId(), e.getMarketplaceOfferId(),
                e.getPriceDecisionId(), e.getExecutionMode(), e.getStatus(),
                e.getTargetPrice(), e.getCurrentPriceAtCreation(),
                e.getApprovedByUserId(), e.getApprovedAt(),
                e.getHoldReason(), e.getCancelReason(),
                e.getSupersededByActionId(), e.getReconciliationSource(),
                e.getManualOverrideReason(),
                e.getAttemptCount(), e.getMaxAttempts(),
                e.getApprovalTimeoutHours(), e.getNextAttemptAt(),
                e.getCreatedAt(), e.getUpdatedAt()
        );
    }

    private PriceActionAttemptResponse toAttemptResponse(PriceActionAttemptEntity e) {
        return new PriceActionAttemptResponse(
                e.getId(), e.getAttemptNumber(),
                e.getStartedAt(), e.getCompletedAt(),
                e.getOutcome(), e.getErrorClassification(),
                e.getErrorMessage(), e.getActorUserId(),
                e.getProviderRequestSummary(), e.getProviderResponseSummary(),
                e.getReconciliationSource(), e.getReconciliationReadAt(),
                e.getActualPrice(), e.getPriceMatch(),
                e.getCreatedAt()
        );
    }
}
