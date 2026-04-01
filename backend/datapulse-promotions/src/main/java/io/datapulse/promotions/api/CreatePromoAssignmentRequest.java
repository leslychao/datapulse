package io.datapulse.promotions.api;

import io.datapulse.promotions.domain.PromoScopeType;
import jakarta.validation.constraints.NotNull;

public record CreatePromoAssignmentRequest(
        @NotNull Long connectionId,
        @NotNull PromoScopeType scopeType,
        Long categoryId,
        Long marketplaceOfferId
) {
}
