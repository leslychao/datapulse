package io.datapulse.etl.domain.normalized;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record NormalizedOrderItem(
        String externalOrderId,
        String sellerSku,
        int quantity,
        BigDecimal pricePerUnit,
        BigDecimal totalAmount,
        String currency,
        OffsetDateTime orderDate,
        String status,
        String fulfillmentType,
        String region
) {}
