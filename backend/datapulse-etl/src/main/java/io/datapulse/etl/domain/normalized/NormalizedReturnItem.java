package io.datapulse.etl.domain.normalized;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record NormalizedReturnItem(
        String externalReturnId,
        String sellerSku,
        String marketplaceProductId,
        int quantity,
        BigDecimal returnAmount,
        String returnReason,
        String currency,
        OffsetDateTime returnDate,
        String status,
        String fulfillmentType
) {}
