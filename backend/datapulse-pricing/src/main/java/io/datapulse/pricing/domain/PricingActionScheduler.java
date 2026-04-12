package io.datapulse.pricing.domain;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.stereotype.Service;

import io.datapulse.platform.outbox.OutboxEventType;
import io.datapulse.platform.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Publishes PRICING_ACTION_REQUESTED events via outbox for CHANGE decisions.
 * The materializer consumer in datapulse-api creates the actual PriceAction
 * via ActionService, handling supersede/defer and scheduling execution.
 * <p>
 * RECOMMENDATION → no action (shown in UI only)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PricingActionScheduler {

    private final OutboxService outboxService;

    public void scheduleAction(long decisionId, long marketplaceOfferId,
                               BigDecimal targetPrice, BigDecimal currentPrice,
                               ExecutionMode executionMode,
                               long connectionId, long workspaceId,
                               int approvalTimeoutHours) {
        if (executionMode == ExecutionMode.RECOMMENDATION) {
            return;
        }

        boolean autoApprove = executionMode == ExecutionMode.FULL_AUTO
                || executionMode == ExecutionMode.SIMULATED;

        Map<String, Object> payload = Map.of(
                "decisionId", decisionId,
                "marketplaceOfferId", marketplaceOfferId,
                "targetPrice", targetPrice.toPlainString(),
                "currentPrice", currentPrice != null ? currentPrice.toPlainString() : "0",
                "executionMode", executionMode.name(),
                "autoApprove", autoApprove,
                "approvalTimeoutHours", approvalTimeoutHours,
                "connectionId", connectionId,
                "workspaceId", workspaceId
        );

        outboxService.createEvent(
                OutboxEventType.PRICING_ACTION_REQUESTED,
                "price_decision",
                decisionId,
                payload);

        log.debug("Pricing action requested: decisionId={}, offerId={}, mode={}, autoApprove={}",
                decisionId, marketplaceOfferId, executionMode, autoApprove);
    }
}
