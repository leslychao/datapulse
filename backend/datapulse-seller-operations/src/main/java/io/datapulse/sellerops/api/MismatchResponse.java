package io.datapulse.sellerops.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record MismatchResponse(
    long mismatchId,
    String type,
    Long offerId,
    String offerName,
    String skuCode,
    String marketplaceType,
    String connectionName,
    String expectedValue,
    String actualValue,
    BigDecimal deltaPct,
    String severity,
    String status,
    String resolution,
    OffsetDateTime detectedAt,
    OffsetDateTime resolvedAt,
    Long relatedActionId
) {
}
