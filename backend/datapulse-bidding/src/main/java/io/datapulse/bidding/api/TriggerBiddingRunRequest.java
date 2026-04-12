package io.datapulse.bidding.api;

import jakarta.validation.constraints.Positive;

public record TriggerBiddingRunRequest(
    @Positive long bidPolicyId
) {
}
