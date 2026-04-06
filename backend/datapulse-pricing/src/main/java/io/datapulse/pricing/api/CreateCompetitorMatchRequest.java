package io.datapulse.pricing.api;

import jakarta.validation.constraints.NotNull;

public record CreateCompetitorMatchRequest(
        @NotNull Long marketplaceOfferId,
        String competitorName,
        String competitorListingUrl
) {
}
