package io.datapulse.promotions.api;

import java.time.OffsetDateTime;

public record PromoCampaignSummaryResponse(
        Long id,
        Long connectionId,
        String externalPromoId,
        String sourcePlatform,
        String promoName,
        String promoType,
        String status,
        OffsetDateTime dateFrom,
        OffsetDateTime dateTo,
        OffsetDateTime freezeAt,
        String description,
        Boolean isParticipating,
        OffsetDateTime syncedAt
) {
}
