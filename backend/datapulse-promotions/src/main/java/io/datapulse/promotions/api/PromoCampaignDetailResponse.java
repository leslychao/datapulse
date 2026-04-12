package io.datapulse.promotions.api;

import java.time.OffsetDateTime;

public record PromoCampaignDetailResponse(
        Long id,
        String externalPromoId,
        String sourcePlatform,
        String promoName,
        String promoType,
        String status,
        OffsetDateTime dateFrom,
        OffsetDateTime dateTo,
        OffsetDateTime freezeAt,
        String description,
        String mechanic,
        Boolean isParticipating,
        OffsetDateTime syncedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        int totalProducts,
        int eligibleCount,
        int participatingCount,
        int declinedCount
) {
}
