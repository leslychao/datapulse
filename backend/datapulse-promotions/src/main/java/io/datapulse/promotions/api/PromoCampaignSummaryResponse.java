package io.datapulse.promotions.api;

import java.time.OffsetDateTime;

public record PromoCampaignSummaryResponse(
        Long id,
        String promoName,
        String sourcePlatform,
        String promoType,
        String mechanic,
        OffsetDateTime dateFrom,
        OffsetDateTime dateTo,
        OffsetDateTime freezeAt,
        int eligibleCount,
        int participatedCount,
        String status,
        Long connectionId,
        String connectionName
) {
}
