package io.datapulse.pricing.api;

import io.datapulse.pricing.domain.ScopeType;
import jakarta.validation.constraints.NotNull;

public record CreateAssignmentRequest(
        @NotNull Long connectionId,
        @NotNull ScopeType scopeType,
        Long categoryId,
        Long marketplaceOfferId
) {
}
