package io.datapulse.pricing.api;

import io.datapulse.pricing.domain.ScopeType;

public record AssignmentResponse(
        Long id,
        Long pricePolicyId,
        Long marketplaceConnectionId,
        ScopeType scopeType,
        Long categoryId,
        Long marketplaceOfferId
) {
}
