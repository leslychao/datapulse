package io.datapulse.bidding.persistence;

public record BidPolicyAssignmentRow(
    long id,
    long bidPolicyId,
    String strategyType,
    String executionMode,
    String config
) {
}
