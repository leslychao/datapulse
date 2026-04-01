package io.datapulse.pricing.persistence;

import java.math.BigDecimal;

public record ImpactPreviewRow(
    long offerId,
    String offerName,
    String sellerSku,
    String offerStatus,
    BigDecimal currentPrice,
    BigDecimal cogs,
    boolean hasManualLock
) {
}
