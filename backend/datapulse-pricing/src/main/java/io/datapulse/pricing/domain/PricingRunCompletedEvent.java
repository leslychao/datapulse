package io.datapulse.pricing.domain;

/**
 * Published when a pricing run reaches a terminal state (COMPLETED, FAILED, CANCELLED).
 * No listeners yet — intended for alert on FAILED and STOMP push.
 */
public record PricingRunCompletedEvent(
        long pricingRunId,
        long workspaceId,
        long connectionId,
        int changeCount,
        int skipCount,
        int holdCount,
        RunStatus finalStatus
) {
}
