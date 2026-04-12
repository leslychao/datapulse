package io.datapulse.promotions.api;

import io.datapulse.promotions.domain.PromoScopeType;
import jakarta.validation.constraints.NotNull;

public record CreatePromoAssignmentRequest(
        @NotNull String sourcePlatform,
        @NotNull PromoScopeType scopeType,
        Long categoryId,
        Long marketplaceOfferId
) {
}
