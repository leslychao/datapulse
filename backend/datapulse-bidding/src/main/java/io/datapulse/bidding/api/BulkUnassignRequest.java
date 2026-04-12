package io.datapulse.bidding.api;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BulkUnassignRequest(
    @NotEmpty List<Long> marketplaceOfferIds
) {
}
