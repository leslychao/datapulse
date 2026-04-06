package io.datapulse.pricing.api;

import java.time.OffsetDateTime;

public record CompetitorMatchResponse(
        long id,
        long workspaceId,
        long marketplaceOfferId,
        String competitorName,
        String competitorListingUrl,
        String matchMethod,
        String trustLevel,
        Long matchedBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
