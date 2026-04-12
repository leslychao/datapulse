package io.datapulse.bidding.api;

import java.time.OffsetDateTime;

public record AssignmentResponse(
    long id,
    long bidPolicyId,
    Long marketplaceOfferId,
    String campaignExternalId,
    String scope,
    OffsetDateTime createdAt
) {
}
