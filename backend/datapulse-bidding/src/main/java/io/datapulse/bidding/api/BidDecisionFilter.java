package io.datapulse.bidding.api;

import java.time.LocalDate;

public record BidDecisionFilter(
    Long workspaceId,
    Long bidPolicyId,
    Long marketplaceOfferId,
    String decisionType,
    LocalDate dateFrom,
    LocalDate dateTo
) {
}
