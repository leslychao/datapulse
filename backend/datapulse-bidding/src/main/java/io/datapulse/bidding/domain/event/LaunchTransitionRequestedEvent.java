package io.datapulse.bidding.domain.event;

import io.datapulse.bidding.domain.BiddingStrategyType;

public record LaunchTransitionRequestedEvent(
    long workspaceId,
    long marketplaceOfferId,
    long bidPolicyId,
    BiddingStrategyType targetStrategy
) {}
