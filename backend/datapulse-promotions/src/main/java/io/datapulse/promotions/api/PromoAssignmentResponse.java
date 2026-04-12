package io.datapulse.promotions.api;

import io.datapulse.promotions.domain.PromoScopeType;

import java.time.OffsetDateTime;

public record PromoAssignmentResponse(
        Long id,
        Long promoPolicyId,
        String connectionName,
        String marketplace,
        PromoScopeType scopeType,
        Long categoryId,
        Long marketplaceOfferId,
        OffsetDateTime createdAt
) {
}
