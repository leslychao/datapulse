package io.datapulse.pricing.api;

import io.datapulse.pricing.domain.ScopeType;

public record AssignmentResponse(
    Long id,
    Long pricePolicyId,
    String connectionName,
    String marketplace,
    ScopeType scopeType,
    Long categoryId,
    String categoryName,
    Long marketplaceOfferId,
    String offerName,
    String sellerSku
) {
}
