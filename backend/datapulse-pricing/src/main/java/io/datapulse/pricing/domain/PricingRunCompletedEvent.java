package io.datapulse.pricing.domain;

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
