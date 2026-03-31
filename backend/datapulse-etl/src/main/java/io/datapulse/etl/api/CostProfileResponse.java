package io.datapulse.etl.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record CostProfileResponse(
        long id,
        long sellerSkuId,
        String skuCode,
        BigDecimal costPrice,
        String currency,
        LocalDate validFrom,
        LocalDate validTo,
        long updatedByUserId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
