package io.datapulse.execution.api;

import io.datapulse.common.exception.ConflictException;
import io.datapulse.common.exception.NotFoundException;
import io.datapulse.execution.domain.ActionService;
import io.datapulse.execution.domain.PriceActionFilter;
import io.datapulse.execution.domain.ReconciliationService;
import io.datapulse.execution.persistence.PriceActionAttemptEntity;
import io.datapulse.execution.persistence.PriceActionDetailRow;
import io.datapulse.execution.persistence.PriceActionSummaryRow;
import io.datapulse.execution.persistence.PriceActionTransitionRow;
import io.datapulse.platform.security.WorkspaceContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestController
@RequestMapping(value = "/api/workspaces/{workspaceId}/actions",
    produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class PriceActionController {

    private final ActionService actionService;
    private final ReconciliationService reconciliationService;
    private final WorkspaceContext workspaceContext;

    @GetMapping
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
    public Page<PriceActionSummaryResponse> list(
            @PathVariable("workspaceId") long workspaceId,
            PriceActionFilter filter,
            Pageable pageable) {
        return actionService.listActions(workspaceId, filter, pageable)
                .map(this::toSummaryResponse);
    }

    @GetMapping("/kpi")
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
    public PriceActionKpiResponse getKpi(@PathVariable("workspaceId") long workspaceId) {
        var row = actionService.getActionsKpi(workspaceId);
        return new PriceActionKpiResponse(
            row.total(), row.pending(), row.executing(), row.failed());
    }

    @GetMapping("/{actionId}")
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
    public PriceActionDetailResponse get(
            @PathVariable("workspaceId") long workspaceId,
            @PathVariable("actionId") long actionId) {
        var detail = actionService.getActionDetailRow(actionId);
        var attempts = actionService.getAttempts(actionId);
        var transitions = actionService.getTransitions(actionId);
        return toDetailResponse(detail, attempts, transitions);
    }

    @GetMapping("/{actionId}/attempts")
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
    public List<PriceActionAttemptResponse> listAttempts(
            @PathVariable("workspaceId") long workspaceId,
            @PathVariable("actionId") long actionId) {
        return actionService.getAttempts(actionId).stream()
                .map(this::toAttemptResponse)
                .toList();
    }

    @PostMapping("/{actionId}/approve")
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)"
        + " and hasAnyAuthority('ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
    public void approve(@PathVariable("workspaceId") long workspaceId,
                        @PathVariable("actionId") long actionId) {
        actionService.casApprove(actionId, workspaceContext.getUserId());
    }

    @PostMapping("/bulk-approve")
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)"
        + " and hasAnyAuthority('ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
    public BulkActionResponse bulkApprove(
        @PathVariable("workspaceId") long workspaceId,
        @Valid @RequestBody BulkApproveRequest request) {
        int processed = 0;
        int skipped = 0;
        int errored = 0;
        List<String> errors = new ArrayList<>();

        for (long actionId : request.actionIds()) {
            try {
                actionService.casApprove(actionId, workspaceContext.getUserId());
                processed++;
            } catch (NotFoundException | ConflictException e) {
                skipped++;
            } catch (Exception e) {
                errored++;
                errors.add("actionId=%d: %s".formatted(actionId, e.getMessage()));
                log.warn("Bulk approve failed: actionId={}, error={}", actionId, e.getMessage());
            }
        }

        return new BulkActionResponse(processed, skipped, errored, errors);
    }

    @PostMapping("/bulk-reject")
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId) "
        + "and hasAnyAuthority('ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
    public BulkActionResponse bulkReject(
        @PathVariable("workspaceId") long workspaceId,
        @Valid @RequestBody BulkRejectRequest request) {
        int processed = 0;
        int skipped = 0;
        int errored = 0;
        List<String> errors = new ArrayList<>();

        for (long actionId : request.actionIds()) {
            try {
                actionService.casReject(actionId, request.cancelReason());
                processed++;
            } catch (NotFoundException | ConflictException e) {
                skipped++;
            } catch (Exception e) {
                errored++;
                errors.add("actionId=%d: %s".formatted(actionId, e.getMessage()));
                log.warn("Bulk reject failed: actionId={}, error={}", actionId, e.getMessage());
            }
        }

        return new BulkActionResponse(processed, skipped, errored, errors);
    }

    @PostMapping("/{actionId}/reject")
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)"
        + " and hasAnyAuthority('ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
    public void reject(@PathVariable("workspaceId") long workspaceId,
                        @PathVariable("actionId") long actionId,
                        @Valid @RequestBody CancelRequest request) {
        actionService.casReject(actionId, request.cancelReason());
    }

    @PostMapping("/{actionId}/hold")
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)"
        + " and hasAnyAuthority('ROLE_OPERATOR', 'ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
    public void hold(@PathVariable("workspaceId") long workspaceId,
                      @PathVariable("actionId") long actionId,
                      @Valid @RequestBody HoldRequest request) {
        actionService.casHold(actionId, request.holdReason());
    }

    @PostMapping("/{actionId}/resume")
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)"
        + " and hasAnyAuthority('ROLE_OPERATOR', 'ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
    public void resume(@PathVariable("workspaceId") long workspaceId,
                        @PathVariable("actionId") long actionId) {
        actionService.casResume(actionId, workspaceContext.getUserId());
    }

    @PostMapping("/{actionId}/cancel")
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)"
        + " and hasAnyAuthority('ROLE_OPERATOR', 'ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
    public void cancel(@PathVariable("workspaceId") long workspaceId,
                        @PathVariable("actionId") long actionId,
                        @Valid @RequestBody CancelRequest request) {
        actionService.casCancel(actionId, request.cancelReason());
    }

    @PostMapping("/{actionId}/retry")
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)"
        + " and hasAnyAuthority('ROLE_PRICING_MANAGER', 'ROLE_ADMIN', 'ROLE_OWNER')")
    public void retry(@PathVariable("workspaceId") long workspaceId,
                       @PathVariable("actionId") long actionId,
                       @Valid @RequestBody RetryRequest request) {
        actionService.retryFailed(actionId, request.retryReason());
    }

    @PostMapping("/{actionId}/reconcile")
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)"
        + " and hasAnyAuthority('ROLE_ADMIN', 'ROLE_OWNER')")
    public void reconcile(@PathVariable("workspaceId") long workspaceId,
                           @PathVariable("actionId") long actionId,
                           @Valid @RequestBody ReconcileRequest request) {
        boolean succeeded =
                request.outcome() == ReconcileRequest.ReconcileOutcome.SUCCEEDED;
        reconciliationService.processManualReconciliation(
                actionId, succeeded, request.manualOverrideReason(),
                workspaceContext.getUserId());
    }

    private PriceActionSummaryResponse toSummaryResponse(PriceActionSummaryRow row) {
        BigDecimal deltaPct = null;
        if (row.currentPriceAtCreation() != null
                && row.currentPriceAtCreation().compareTo(BigDecimal.ZERO) != 0
                && row.targetPrice() != null) {
            deltaPct = row.targetPrice()
                    .subtract(row.currentPriceAtCreation())
                    .multiply(BigDecimal.valueOf(100))
                    .divide(row.currentPriceAtCreation(), 2, RoundingMode.HALF_UP);
        }
        return new PriceActionSummaryResponse(
                row.id(), row.offerName(), row.sku(),
                row.marketplace(), row.connectionName(),
                row.executionMode(), row.status(),
                row.targetPrice(), row.currentPriceAtCreation(), deltaPct,
                row.attemptCount(), row.maxAttempts(),
                row.createdAt(), row.updatedAt()
        );
    }

    private PriceActionDetailResponse toDetailResponse(
        PriceActionDetailRow row,
        List<PriceActionAttemptEntity> attempts,
        List<PriceActionTransitionRow> transitions) {

        BigDecimal deltaPct = null;
        if (row.currentPriceAtCreation() != null
            && row.currentPriceAtCreation().compareTo(BigDecimal.ZERO) != 0
            && row.targetPrice() != null) {
            deltaPct = row.targetPrice()
                .subtract(row.currentPriceAtCreation())
                .multiply(BigDecimal.valueOf(100))
                .divide(row.currentPriceAtCreation(), 2, RoundingMode.HALF_UP);
        }

        String lastErrorMessage = attempts.stream()
            .filter(a -> a.getErrorMessage() != null)
            .reduce((first, second) -> second)
            .map(PriceActionAttemptEntity::getErrorMessage)
            .orElse(null);

        List<PriceActionAttemptResponse> attemptResponses = attempts.stream()
            .map(this::toAttemptResponse)
            .toList();

        List<PriceActionStateTransitionResponse> transitionResponses = transitions.stream()
            .map(t -> new PriceActionStateTransitionResponse(
                t.fromStatus(), t.toStatus(), t.createdAt(),
                t.actorName(), t.reason()))
            .toList();

        return new PriceActionDetailResponse(
            row.id(),
            "SET_PRICE",
            row.offerName(),
            row.marketplaceOfferId(),
            row.sku(),
            row.marketplace(),
            row.connectionName(),
            row.status(),
            row.executionMode(),
            row.targetPrice(),
            row.currentPriceAtCreation(),
            deltaPct,
            row.attemptCount(),
            row.maxAttempts(),
            row.createdAt(),
            row.updatedAt(),
            null,
            row.approvedByName(),
            row.approvedAt(),
            row.holdReason(),
            row.cancelReason(),
            lastErrorMessage,
            row.supersededByActionId(),
            attemptResponses,
            transitionResponses
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
