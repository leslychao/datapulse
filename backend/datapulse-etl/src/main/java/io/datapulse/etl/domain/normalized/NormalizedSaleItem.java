package io.datapulse.etl.domain.normalized;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record NormalizedSaleItem(
        String externalSaleId,
        String sellerSku,
        int quantity,
        BigDecimal saleAmount,
        BigDecimal commission,
        String currency,
        OffsetDateTime saleDate
) {}
