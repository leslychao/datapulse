package io.datapulse.etl.domain.normalized;

import java.time.OffsetDateTime;

public record NormalizedPromoCampaign(
        String externalPromoId,
        String promoName,
        String promoType,
        String status,
        OffsetDateTime dateFrom,
        OffsetDateTime dateTo,
        OffsetDateTime freezeAt,
        String description,
        String mechanic,
        Boolean isParticipating,
        String rawPayload
) {}
