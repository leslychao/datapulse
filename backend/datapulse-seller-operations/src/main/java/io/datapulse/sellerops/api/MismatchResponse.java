package io.datapulse.sellerops.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record MismatchResponse(
    long mismatchId,
    String type,
    Long offerId,
    String offerName,
    String skuCode,
    String expectedValue,
    String actualValue,
    BigDecimal deltaPct,
    String severity,
    String status,
    OffsetDateTime detectedAt,
    String connectionName
) {
}
