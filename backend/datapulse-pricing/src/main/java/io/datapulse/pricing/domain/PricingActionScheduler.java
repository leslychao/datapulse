package io.datapulse.pricing.domain;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.stereotype.Service;

import io.datapulse.platform.outbox.OutboxEventType;
import io.datapulse.platform.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Schedules price actions via outbox for CHANGE decisions.
 * Action creation depends on execution mode:
 * - RECOMMENDATION → no action (shown in UI only)
 * - SEMI_AUTO → action PENDING_APPROVAL
 * - FULL_AUTO → action APPROVED
 * - SIMULATED → action APPROVED with execution_mode=SIMULATED
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PricingActionScheduler {

    private final OutboxService outboxService;

    public void scheduleAction(long decisionId, long marketplaceOfferId,
                               BigDecimal targetPrice, ExecutionMode executionMode,
                               long workspaceId) {
        if (executionMode == ExecutionMode.RECOMMENDATION) {
            return;
        }

        String actionStatus = resolveInitialActionStatus(executionMode);
        String decisionExecutionMode = resolveDecisionExecutionMode(executionMode);

        Map<String, Object> payload = Map.of(
                "decisionId", decisionId,
                "marketplaceOfferId", marketplaceOfferId,
                "targetPrice", targetPrice.toPlainString(),
                "actionStatus", actionStatus,
                "executionMode", decisionExecutionMode,
                "workspaceId", workspaceId
        );

        outboxService.createEvent(
                OutboxEventType.PRICE_ACTION_EXECUTE,
                "price_decision",
                decisionId,
                payload);

        log.debug("Scheduled price action: decisionId={}, offerId={}, status={}, mode={}",
                decisionId, marketplaceOfferId, actionStatus, decisionExecutionMode);
    }

    private String resolveInitialActionStatus(ExecutionMode mode) {
        return switch (mode) {
            case SEMI_AUTO -> "PENDING_APPROVAL";
            case FULL_AUTO, SIMULATED -> "APPROVED";
            case RECOMMENDATION -> throw new IllegalArgumentException(
                    "RECOMMENDATION does not create actions");
        };
    }

    private String resolveDecisionExecutionMode(ExecutionMode mode) {
        return mode == ExecutionMode.SIMULATED ? "SIMULATED" : "LIVE";
    }
}
