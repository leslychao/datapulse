package io.datapulse.pricing.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ManualLockResponse(
        Long id,
        Long marketplaceOfferId,
        BigDecimal lockedPrice,
        String reason,
        Long lockedBy,
        OffsetDateTime lockedAt,
        OffsetDateTime expiresAt,
        OffsetDateTime unlockedAt,
        Long unlockedBy
) {
}
