package io.datapulse.bidding.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BulkAssignRequest(
    @NotEmpty List<Long> marketplaceOfferIds,
    @NotBlank String scope
) {
}
